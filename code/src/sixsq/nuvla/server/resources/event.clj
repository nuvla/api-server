(ns sixsq.nuvla.server.resources.event
  "
Event resources provide a timestamp for the occurrence of some action. These
are used within the SlipStream server to mark changes in the lifecycle of a
cloud application and for other important actions.
"
  (:require
    [sixsq.nuvla.auth.acl_resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.event :as event]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))

;;TODO ACL event why anon can add events?
(def collection-acl {:owners   ["group/nuvla-admin"]
                     :edit-acl ["group/nuvla-anon"]})



;;
;; "Implementations" of multimethod declared in crud namespace
;;

(def validate-fn (u/create-spec-validation-fn ::event/event))
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
  [resource request]
  (try
    (a/can-edit-acl? resource request)
    (let [href (:id resource)
          ^String resource-type (:resource-type resource)
          ops (if (u/is-collection? resource-type)
                [{:rel (:add c/action-uri) :href href}]
                [{:rel (:delete c/action-uri) :href href}])]
      (assoc resource :operations ops))
    (catch Exception e
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
  (std-crud/initialize resource-type ::event/event))
