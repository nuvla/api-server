(ns sixsq.nuvla.server.resources.user-identifier
  "
The UserIdentifier resources provide a mapping between an external identity
(for a given authentication method) and a registered user. Multiple external
identities can be mapped to the same SlipStream user, allowing that user to
authenticate in different ways while using the same account.

This resource follows the standard CIMI SCRUD patterns. However, the resource
`id` is a hashed value of the `identifier`. This guarantees that a single
external identifier cannot be mapped to more than one user.

Users will normally not be concerned with these resources, although they can
list them to see what authentication methods are mapped to their accounts.

Administrators may create new UserIdentifier resources to allow a user to have
more than one authentication method.

> WARNING: Because the resource identifier and the resource id are linked, you
cannot 'edit' the `identifier` field of a UserIdentifier resource; doing so
will invalidate resource. If you want to change an external identifier, you
must delete the old one and create a new one.
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-identifier :as user-identifier]
    [sixsq.nuvla.server.util.metadata :as gen-md]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-name (u/ns->collection-type *ns*))

(def ^:const collection-uri collection-name)

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}})

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
  (let [[_ user] (u/split-resource-id user-id)]
    {:owner {:principal "ADMIN"
             :type      "ROLE"}
     :rules [{:principal user
              :type      "USER"
              :right     "VIEW"}]}))


(defmethod crud/add-acl resource-type
  [{{user-id :href} :user :as resource} request]
  (assoc resource :acl (user-acl user-id)))


;;
;; ids for these resources are the hashed :identifier value
;;

(defmethod crud/new-identifier resource-type
  [{:keys [identifier] :as resource} resource-name]
  (->> identifier
       u/md5
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


(def query-impl (std-crud/query-fn resource-type collection-acl collection-uri))

(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-type ::user-identifier/schema)
  (md/register (gen-md/generate-metadata ::ns ::user-identifier/schema)))
