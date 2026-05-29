(ns com.sixsq.nuvla.server.resources.mepm
  "
MEC Platform Manager (MEPM) resource represents an external platform manager
that Nuvla (MEO) communicates with via the selected Mm3-oriented southbound
interface used in this MEC MVP path.

MEPMs manage host-level operations such as:
- Application lifecycle on specific MEC hosts
- Platform service configuration
- Resource management at the host level

This resource enables Nuvla to act as a MEC Orchestrator (MEO) that coordinates
with multiple MEPMs across distributed edge infrastructure.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.middleware.base-uri :as base-uri]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.event-context :as ectx]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.mepm :as mepm-spec]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as time]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))

(def ^:private default-backend-mode
  "MOCK")


;; Only authenticated users can view and manage MEPMs
(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; Events
;;

(defmethod ec/events-enabled? resource-type
  [_resource-type]
  true)

(defmethod ec/log-event? "mepm.add"
  [_event _response]
  true)

(defmethod ec/log-event? "mepm.edit"
  [_event _response]
  true)

(defmethod ec/log-event? "mepm.delete"
  [_event _response]
  true)


;;
;; Resource metadata
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::mepm-spec/schema))


;;
;; Initialization
;;

(def initialization-order 120)

(defn initialize
  []
  (std-crud/initialize resource-type ::mepm-spec/schema)
  (md/register resource-metadata))


;;
;; Validation
;;

(def validate-fn (u/create-spec-validation-fn ::mepm-spec/schema))

(defn- normalize-endpoint
  [endpoint]
  (some-> endpoint
          str
          str/trim
          (str/replace #"/+$" "")))

(defn- normalize-managed-edge-id
  [edge-id]
  (some-> edge-id
          str
          str/trim
          not-empty))

(defn- normalize-backend-mode
  [backend-mode]
  (some-> backend-mode
          str
          str/trim
          str/upper-case
          not-empty))

(def ^:private allowed-capability-keys
  [:platforms :services :api-version])

(def ^:private allowed-resource-keys
  [:cpu-cores :memory-gb :storage-gb :gpu-count])

(defn- sanitize-map
  [value allowed-keys]
  (let [sanitized (some-> value
                          (select-keys allowed-keys)
                          not-empty)]
    (if (contains? #{nil {}} value)
      value
      sanitized)))

(defn- normalize-capabilities
  [capabilities]
  (sanitize-map capabilities allowed-capability-keys))

(defn- normalize-resources
  [resources]
  (sanitize-map resources allowed-resource-keys))

(defn managed-edge-ids
  "Returns the MEPM managed edge ids with `managed-edges` taking precedence over
   the legacy single-host `mec-host-id` field."
  [mepm]
  (let [managed-edges (->> (:managed-edges mepm)
                           (map normalize-managed-edge-id)
                           (remove nil?)
                           distinct
                           vec)]
    (if (contains? mepm :managed-edges)
      managed-edges
      (cond-> []
        (:mec-host-id mepm) (conj (:mec-host-id mepm))))))

(defn- normalize-managed-edges
  [managed-edges mec-host-id]
  (let [normalized-edges (->> (concat managed-edges
                                      (when mec-host-id [mec-host-id]))
                              (map normalize-managed-edge-id)
                              (remove nil?)
                              distinct
                              vec)]
    (cond-> {:managed-edges normalized-edges}
      (seq normalized-edges) (assoc :mec-host-id (first normalized-edges)))))

(defn- normalize-mepm-fields
  ([resource]
   (normalize-mepm-fields resource nil))
  ([resource request]
   (let [{:keys [managed-edges mec-host-id endpoint backend-mode capabilities resources]} resource
         request-body                             (:body request)
         edge-fields-present?                     (or (contains? resource :managed-edges)
                                                     (contains? resource :mec-host-id))
         explicit-empty-managed-edges?            (and (map? request-body)
                                                      (contains? request-body :managed-edges)
                                                      (empty? (:managed-edges request-body))
                                                      (not (contains? request-body :mec-host-id)))
         effective-mec-host-id                    (if explicit-empty-managed-edges?
                                                    nil
                                                    mec-host-id)
         managed-edge-fields                      (when edge-fields-present?
                                                   (normalize-managed-edges managed-edges effective-mec-host-id))
         cleared-managed-edges?                   (or explicit-empty-managed-edges?
                                                      (and edge-fields-present?
                                                           (empty? (:managed-edges managed-edge-fields))))
         normalized-backend-mode                  (or (normalize-backend-mode backend-mode)
                                                      (when (contains? resource :backend-mode)
                                                        default-backend-mode))
         normalized-capabilities                  (when (contains? resource :capabilities)
                                                   (normalize-capabilities capabilities))
         normalized-resources                     (when (contains? resource :resources)
                                                   (normalize-resources resources))]
     (cond-> (merge resource managed-edge-fields)
       cleared-managed-edges? (dissoc :mec-host-id)
       endpoint (assoc :endpoint (normalize-endpoint endpoint))
       normalized-backend-mode (assoc :backend-mode normalized-backend-mode)
       (contains? resource :capabilities) (assoc :capabilities normalized-capabilities)
       (contains? resource :resources) (assoc :resources normalized-resources)))))

(defn- find-mepm-by-endpoint
  [endpoint]
  (let [normalized-endpoint (normalize-endpoint endpoint)
        [_ resources]       (crud/query-as-admin resource-type {:last 1000})]
    (some #(when (= normalized-endpoint (normalize-endpoint (:endpoint %)))
             %)
          resources)))


(def ^:private mm3-lifecycle-callback-path
  "mec/internal/mm3/app_lcm/v1/notifications")


(def ^:private mm3-lifecycle-notification-types
  ["AppInstNotification"
   "AppLcmOpOccNotification"])


(defn- lifecycle-callback-uri
  [request]
  (str (base-uri/construct-base-uri request) mm3-lifecycle-callback-path))


(defn- lifecycle-subscription-request
  [request]
  {:callbackUri       (lifecycle-callback-uri request)
   :notificationTypes mm3-lifecycle-notification-types})


(defn- persist-lifecycle-subscription!
  [mepm subscription-id callback-uri]
  (let [updated-mepm (assoc mepm
                       :mm3-subscription-id subscription-id
                       :mm3-subscription-callback-uri callback-uri
                       :updated (time/now-str))]
    (db/edit updated-mepm)
    updated-mepm))


(defn- ensure-lifecycle-subscription!
  [mepm request]
  (if (:mm3-subscription-id mepm)
    {:created?     false
     :subscription-id (:mm3-subscription-id mepm)
     :callback-uri (or (:mm3-subscription-callback-uri mepm)
                       (lifecycle-callback-uri request))
     :mepm         mepm}
    (let [subscription-request (lifecycle-subscription-request request)
          callback-uri         (:callbackUri subscription-request)
          result               (mm3/create-subscription (:endpoint mepm) subscription-request {:retry-attempts 1})]
      (if (:success? result)
        (let [subscription-id (or (get-in result [:data :id])
                                  (get-in result [:data :subscriptionId]))]
          (when-not subscription-id
            (throw (ex-info "MEPM lifecycle subscription response did not include an id"
                            {:mepm-id (:id mepm)
                             :endpoint (:endpoint mepm)})))
          {:created?        true
           :subscription-id subscription-id
           :callback-uri    callback-uri
           :mepm            (persist-lifecycle-subscription! mepm subscription-id callback-uri)})
        (throw (ex-info (str "Failed to create southbound lifecycle subscription: " (:message result))
                        {:status   (or (:status result) 503)
                         :mepm-id  (:id mepm)
                         :endpoint (:endpoint mepm)
                         :error    (:error result)}))))))

(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [{{:keys [name endpoint capabilities status] :as body} :body :as request}]
  (let [normalized-endpoint (normalize-endpoint endpoint)
        _                  (when-let [existing (find-mepm-by-endpoint normalized-endpoint)]
                             (throw (r/ex-response
                                      (str "MEPM endpoint already exists: " normalized-endpoint)
                                      409
                                      (:id existing))))
        authn-info    (auth/current-authentication request)
        current-user  (auth/current-user-id request)
        desc-attr     (u/select-desc-keys body)
        mepm-resource (-> (cond-> (merge desc-attr
                                     {:resource-type resource-type
                                      :name          name
                                      :endpoint      normalized-endpoint
                                      :capabilities  capabilities
                                      :backend-mode  (or (normalize-backend-mode (:backend-mode body))
                                                         default-backend-mode)
                                      :status        (or status "ONLINE")
                                      :created       (time/now-str)
                                      :updated       (time/now-str)})
                              (:description body) (assoc :description (:description body))
                              (:mec-host-id body) (assoc :mec-host-id (:mec-host-id body))
                              (contains? body :managed-edges) (assoc :managed-edges (:managed-edges body))
                              (:backend-mode body) (assoc :backend-mode (:backend-mode body))
                              (:resources body) (assoc :resources (:resources body))
                              (:credential-id body) (assoc :credential-id (:credential-id body))
                              (:version body) (assoc :version (:version body))
                              (:tags body) (assoc :tags (:tags body)))
                         (normalize-mepm-fields request))]
    (let [response (add-impl (assoc request :body mepm-resource))
          mepm-id  (get-in response [:body :resource-id])]
      (when mepm-id
        (try
          (ensure-lifecycle-subscription! (crud/retrieve-by-id-as-admin mepm-id) request)
          (catch Exception e
            (log/warn e "Unable to create southbound lifecycle subscription during MEPM registration" mepm-id))))
      response)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type
                                 :pre-validate-hook (fn [resource request]
                                                      (normalize-mepm-fields resource request))))

(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; ACL
;;

(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (a/add-acl resource request)))


;;
;; Actions
;;

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations conj (u/action-map id :check-health))
            can-manage? (update :operations conj (u/action-map id :query-capabilities))
            can-manage? (update :operations conj (u/action-map id :query-resources))
            can-manage? (update :operations conj (u/action-map id :ensure-lifecycle-subscription)))))


;;
;; Check health action - query MEPM status via Mm3
;;

(defmethod crud/do-action [resource-type "check-health"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id           (str resource-type "/" uuid)
          mepm         (crud/retrieve-by-id-as-admin id)
          endpoint     (:endpoint mepm)
          current-time (time/now-str)
          
          ;; Perform actual Mm3 health check
          health-result (mm3/check-health endpoint)]
      
      (if (:success? health-result)
        (do
          ;; Update last-check timestamp and status based on health check
          (db/edit (assoc mepm 
                          :last-check current-time
                          :status "ONLINE"
                          :updated current-time))
          (log/info "MEPM" id "health check successful")
          (r/map-response {:message "MEPM health check completed"
                           :status "ONLINE"
                           :last-check current-time
                           :health-data (:data health-result)}
                          200 id))
        (do
          ;; Mark as degraded/offline if health check fails
          (db/edit (assoc mepm
                          :last-check current-time
                          :status "DEGRADED"
                          :updated current-time))
          (log/warn "MEPM" id "health check failed:" (:message health-result))
          (r/map-response {:message (str "Health check failed: " (:message health-result))
                           :status "DEGRADED"
                           :error (:error health-result)}
                          503 id))))
    (catch Exception e
      (log/error e "Failed to check MEPM health")
      (r/map-response (str "Health check failed: " (.getMessage e)) 500))))


;;
;; Query capabilities action - get MEPM capabilities via Mm3
;;

(defmethod crud/do-action [resource-type "query-capabilities"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          mepm     (crud/retrieve-by-id-as-admin id)
          endpoint (:endpoint mepm)
          
          ;; Perform actual Mm3 capabilities query
          cap-result (mm3/query-capabilities endpoint)]
      
      (if (:success? cap-result)
        (let [capabilities (normalize-capabilities (:data cap-result))]
          ;; Update stored capabilities with fresh data from MEPM
          (db/edit (assoc mepm 
                          :capabilities capabilities
                          :updated (time/now-str)))
          (log/info "MEPM" id "capabilities queried successfully")
          (r/map-response capabilities 200 id))
        (do
          (log/warn "MEPM" id "capabilities query failed:" (:message cap-result))
          (r/map-response {:message (str "Capabilities query failed: " (:message cap-result))
                           :error (:error cap-result)
                           :cached-capabilities (:capabilities mepm)}
                          503 id))))
    (catch Exception e
      (log/error e "Failed to query MEPM capabilities")
      (r/map-response (str "Capabilities query failed: " (.getMessage e)) 500))))


;;
;; Query resources action - get available resources via Mm3
;;

(defmethod crud/do-action [resource-type "query-resources"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          mepm     (crud/retrieve-by-id-as-admin id)
          endpoint (:endpoint mepm)
          
          ;; Perform actual Mm3 resources query
          res-result (mm3/query-resources endpoint)]
      
      (if (:success? res-result)
        (let [resources (normalize-resources (:data res-result))]
          ;; Update stored resources with fresh data from MEPM
          (db/edit (assoc mepm 
                          :resources resources
                          :updated (time/now-str)))
          (log/info "MEPM" id "resources queried successfully")
          (r/map-response resources 200 id))
        (do
          (log/warn "MEPM" id "resources query failed:" (:message res-result))
          (r/map-response {:message (str "Resources query failed: " (:message res-result))
                           :error (:error res-result)
                           :cached-resources (:resources mepm)}
                          503 id))))
    (catch Exception e
      (log/error e "Failed to query MEPM resources")
      (r/map-response (str "Resources query failed: " (.getMessage e)) 500))))


(defmethod crud/do-action [resource-type "ensure-lifecycle-subscription"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id                                (str resource-type "/" uuid)
          mepm                              (crud/retrieve-by-id-as-admin id)
          {:keys [created? subscription-id callback-uri]}
          (ensure-lifecycle-subscription! mepm request)]
      (log/info "MEPM" id
                (if created?
                  "registered southbound lifecycle subscription"
                  "already has southbound lifecycle subscription")
                subscription-id)
      (r/map-response {:message        (if created?
                                         "Southbound lifecycle subscription created"
                                         "Southbound lifecycle subscription already present")
                       :subscriptionId subscription-id
                       :callbackUri    callback-uri}
                      200
                      id))
    (catch Exception e
      (log/error e "Failed to ensure southbound lifecycle subscription")
      (r/map-response (str "Southbound lifecycle subscription failed: " (.getMessage e))
                      (or (:status (ex-data e)) 503)))))
