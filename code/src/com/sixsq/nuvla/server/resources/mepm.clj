(ns com.sixsq.nuvla.server.resources.mepm
  "
MEC Platform Manager (MEPM) resource represents an external platform manager
that Nuvla (MEO) communicates with via the Mm5 interface as defined in 
ETSI GS MEC 003.

MEPMs manage host-level operations such as:
- Application lifecycle on specific MEC hosts
- Platform service configuration
- Resource management at the host level

This resource enables Nuvla to act as a MEC Orchestrator (MEO) that coordinates
with multiple MEPMs across distributed edge infrastructure.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
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

(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [{{:keys [name endpoint capabilities status] :as body} :body :as request}]
  (let [authn-info    (auth/current-authentication request)
        current-user  (auth/current-user-id request)
        desc-attr     (u/select-desc-keys body)
        mepm-resource (cond-> (merge desc-attr
                                     {:resource-type resource-type
                                      :name          name
                                      :endpoint      endpoint
                                      :capabilities  capabilities
                                      :status        (or status "ONLINE")
                                      :created       (time/now-str)
                                      :updated       (time/now-str)})
                              (:description body) (assoc :description (:description body))
                              (:mec-host-id body) (assoc :mec-host-id (:mec-host-id body))
                              (:resources body) (assoc :resources (:resources body))
                              (:credential-id body) (assoc :credential-id (:credential-id body))
                              (:version body) (assoc :version (:version body))
                              (:tags body) (assoc :tags (:tags body)))]
    (add-impl (assoc request :body mepm-resource))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))

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
            can-manage? (update :operations conj (u/action-map id :query-resources)))))


;;
;; Check health action - query MEPM status via Mm5
;;

(defmethod crud/do-action [resource-type "check-health"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id           (str resource-type "/" uuid)
          mepm         (crud/retrieve-by-id-as-admin id)
          endpoint     (:endpoint mepm)
          current-time (time/now-str)
          
          ;; Perform actual Mm5 health check
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
;; Query capabilities action - get MEPM capabilities via Mm5
;;

(defmethod crud/do-action [resource-type "query-capabilities"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          mepm     (crud/retrieve-by-id-as-admin id)
          endpoint (:endpoint mepm)
          
          ;; Perform actual Mm5 capabilities query
          cap-result (mm3/query-capabilities endpoint)]
      
      (if (:success? cap-result)
        (let [capabilities (:data cap-result)]
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
;; Query resources action - get available resources via Mm5
;;

(defmethod crud/do-action [resource-type "query-resources"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          mepm     (crud/retrieve-by-id-as-admin id)
          endpoint (:endpoint mepm)
          
          ;; Perform actual Mm5 resources query
          res-result (mm3/query-resources endpoint)]
      
      (if (:success? res-result)
        (let [resources (:data res-result)]
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
