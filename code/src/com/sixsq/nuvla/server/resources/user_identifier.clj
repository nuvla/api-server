(ns com.sixsq.nuvla.server.resources.user-identifier
  "
The `user-identifier` resources provide a mapping between an external identity
(for a given authentication method) and a registered user. Multiple external
identities can be mapped to the same Nuvla user, allowing that user to
authenticate in different ways while using the same account.

This resource follows the standard SCRUD patterns. However, the resource `id`
is a hashed value of the `identifier`. This guarantees that a single external
identifier cannot be mapped to more than one user.

Users will normally not be concerned with these resources, although they can
list them to see what authentication methods are mapped to their accounts.

Administrators may create new `user-identifier` resources to allow a user to
have more than one authentication method.

> WARNING: Because the resource identifier and the resource id are linked, you
cannot 'edit' the `identifier` field of a `user-identifier` resource; doing so
will invalidate resource. If you want to change an external identifier, you
must delete the old one and create a new one.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.user-identifier :as user-identifier]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})


(def resource-acl {:owners ["group/nuvla-admin"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::user-identifier/schema))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; acl must allow users to see their own identifiers
;;

(defn user-acl
  [user-id]
  {:owners   ["group/nuvla-admin"]
   :view-acl [user-id]})


(defmethod crud/add-acl resource-type
  [{:keys [parent] :as resource} _request]
  (assoc resource :acl (user-acl parent)))


;;
;; ids for these resources are the hashed :identifier value
;;

(defmethod crud/new-identifier resource-type
  [{:keys [identifier] :as resource} _resource-name]
  (->> identifier
       u/from-data-uuid
       (str resource-type "/")
       (assoc resource :id)))

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


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::user-identifier/schema))

(def initialization-order 30)

(defn initialize
  []
  (std-crud/initialize resource-type ::user-identifier/schema)
  (md/register resource-metadata))
