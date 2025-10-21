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
    (let [id      (str resource-type "/" uuid)
          mepm    (crud/retrieve-by-id-as-admin id)
          current-time (time/now-str)]
      ;; TODO: Implement actual Mm5 health check when Mm5 client is ready
      ;; For now, just update last-check timestamp
      (db/edit (assoc mepm :last-check current-time :updated current-time))
      (r/map-response "MEPM health check completed" 200 id))
    (catch Exception e
      (log/error "Failed to check MEPM health:" (.getMessage e))
      (r/map-response (str "Health check failed: " (.getMessage e)) 500))))


;;
;; Query capabilities action - get MEPM capabilities via Mm5
;;

(defmethod crud/do-action [resource-type "query-capabilities"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id   (str resource-type "/" uuid)
          mepm (crud/retrieve-by-id-as-admin id)]
      ;; TODO: Implement actual Mm5 capabilities query when Mm5 client is ready
      ;; For now, just return stored capabilities
      (r/map-response (:capabilities mepm) 200 id))
    (catch Exception e
      (log/error "Failed to query MEPM capabilities:" (.getMessage e))
      (r/map-response (str "Capabilities query failed: " (.getMessage e)) 500))))


;;
;; Query resources action - get available resources via Mm5
;;

(defmethod crud/do-action [resource-type "query-resources"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id   (str resource-type "/" uuid)
          mepm (crud/retrieve-by-id-as-admin id)]
      ;; TODO: Implement actual Mm5 resources query when Mm5 client is ready
      ;; For now, just return stored resources
      (r/map-response (:resources mepm) 200 id))
    (catch Exception e
      (log/error "Failed to query MEPM resources:" (.getMessage e))
      (r/map-response (str "Resources query failed: " (.getMessage e)) 500))))
