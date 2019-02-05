(ns com.sixsq.slipstream.ssclj.resources.user-template-direct
  "
Resource that is used to create a user account directly with the provided user
information. Typically this method is available only to service administrators.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-direct :as ut-direct]
    [com.sixsq.slipstream.ssclj.resources.user-template :as p]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


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
