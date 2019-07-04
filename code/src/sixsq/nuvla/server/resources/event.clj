(ns sixsq.nuvla.server.resources.event
  "
The `event` resources provide a timestamp and other information when some
event occurs. These are used, for example, to mark changes in the lifecycle of
an application.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.event :as event]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; "Implementations" of multimethod declared in crud namespace
;;

(def validate-fn (u/create-spec-validation-fn ::event/schema))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


;;
;; available operations
;;

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (if (u/is-collection? resource-type)
    (if (a/can-add? resource request)
      (let [ops [(u/operation-map id :add)]]
        (assoc resource :operations ops))
      (dissoc resource :operations))
    (if (a/can-edit? resource request)
      (let [ops [(u/operation-map id :delete)]]
        (assoc resource :operations ops))
      (dissoc resource :operations))))


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [{{:keys [orderby]} :cimi-params :as request}]
  (query-impl (assoc-in request [:cimi-params :orderby] (if (seq orderby) orderby [["timestamp" :desc]]))))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::event/schema)
  (md/register (gen-md/generate-metadata ::ns ::event/schema)))
