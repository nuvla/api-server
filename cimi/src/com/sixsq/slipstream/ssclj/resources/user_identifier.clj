(ns com.sixsq.slipstream.ssclj.resources.user-identifier
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
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.user-identifier :as user-identifier]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]
    [superstring.core :as str]))

(def ^:const resource-name "UserIdentifier")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "UserIdentifierCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

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
(defmethod crud/validate resource-uri
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


(defmethod crud/add-acl resource-uri
  [{{user-id :href} :user :as resource} request]
  (assoc resource :acl (user-acl user-id)))


;;
;; ids for these resources are the hashed :identifier value
;;

(defmethod crud/new-identifier resource-name
  [{:keys [identifier] :as resource} resource-name]
  (->> identifier
       u/md5
       (str resource-url "/")
       (assoc resource :id)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def edit-impl (std-crud/edit-fn resource-name))


(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def delete-impl (std-crud/delete-fn resource-name))


(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::user-identifier/schema)
  (md/register (gen-md/generate-metadata ::ns ::user-identifier/schema)))
