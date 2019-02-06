(ns sixsq.nuvla.server.resources.user-template-direct
  "
Resource that is used to create a user account directly with the provided user
information. Typically this method is available only to service administrators.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-direct :as ut-direct]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "direct")


(def ^:const resource-name "Direct")


(def ^:const resource-url registration-method)


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ADMIN"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; resource
;;

(def ^:const resource
  {:method           registration-method
   :instance         registration-method
   :name             "Direct"
   :description      "Direct creation of user by the administrator"
   :resourceMetadata (str p/resource-url "-" registration-method)
   :username         "username"
   :password         "password"
   :firstName        "John"
   :lastName         "Doe"
   :emailAddress     "user@example.com"
   :organization     ""
   :roles            ""
   :group            "administrator"
   :icon             "key"
   :hidden           true
   :order            0
   :acl              resource-acl})



;;
;; initialization: register this user template and create direct registration template
;;

(defn initialize
  []
  (p/register registration-method)
  (std-crud/initialize p/resource-url ::ut-direct/schema)
  (std-crud/add-if-absent (str p/resource-url "/" registration-method) p/resource-url resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ut-direct/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ut-direct/schema))
(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
