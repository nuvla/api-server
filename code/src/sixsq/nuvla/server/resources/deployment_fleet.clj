(ns sixsq.nuvla.server.resources.deployment-fleet
  "
These resources represent a deployment fleet that regroups deployments.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment-fleet :as spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-type (u/ns->collection-type *ns*))

(def ^:const create-type (u/ns->create-type *ns*))

(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]})

(def actions [{:name           "start"
               :uri            "start"
               :description    "start deployment fleet"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "stop"
               :uri            "stop"
               :description    "stop deployment fleet"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])

;;
;; validate deployment fleet
;;

(def validate-fn (u/create-spec-validation-fn ::spec/deployment-fleet))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (add-impl request))

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

(defn can-start?
  [{:keys [state] :as _resource}]
  (contains? #{"CREATED" "STOPPED"} state))

(defn can-stop?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED"} state))

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [start-op            (u/action-map id :start)
        stop-op             (u/action-map id :stop)
        can-manage?         (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)

            (and can-manage? (can-start? resource)) (update :operations conj start-op)

            (and can-manage? (can-stop? resource))
            (update :operations conj stop-op))))



;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::spec/deployment-fleet))


(defn initialize
  []
  (std-crud/initialize resource-type ::spec/deployment-fleet)
  (md/register resource-metadata))
