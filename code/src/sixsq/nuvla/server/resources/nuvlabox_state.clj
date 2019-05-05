(ns sixsq.nuvla.server.resources.nuvlabox-state
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.nuvlabox-state :as nuvlabox-state]
    [sixsq.nuvla.auth.utils :as auth-utils]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-admin"]
                     :query ["group/nuvla-user"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::nuvlabox-state/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(defn create-nuvlabox-state
  "Utility to facilitate creating a new nuvlabox-state resource from the
   nuvlabox-record resource. This will create (as an administrator) a new state
   based on the given id and acl. The returned value is the standard 'add'
   response for the request."
  [nuvlabox-id nuvlabox-acl]
  (let [body {:resource-type resource-type
              :parent        nuvlabox-id
              :state         "NEW"
              :acl           nuvlabox-acl}
        nuvlabox-state-request {:params      {:resource-name resource-type}
                                :nuvla/authn auth-utils/internal-identity
                                :body        body}]
    (add-impl nuvlabox-state-request)))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox-state/schema))
