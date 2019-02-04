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
;; description
;;

(def ^:const desc
  (merge p/UserTemplateDescription
         {:username     {:displayName "Username"
                         :category    "general"
                         :description "username"
                         :type        "string"
                         :mandatory   true
                         :readOnly    false
                         :order       20}
          :emailAddress {:displayName "Email Address"
                         :category    "general"
                         :description "email address"
                         :type        "string"
                         :mandatory   true
                         :readOnly    false
                         :order       21}
          :firstName    {:displayName "First Name"
                         :category    "general"
                         :description "user's given name"
                         :type        "string"
                         :mandatory   false
                         :readOnly    false
                         :order       22}
          :lastName     {:displayName "Last Name"
                         :category    "general"
                         :description "user's last or family name"
                         :type        "string"
                         :mandatory   false
                         :readOnly    false
                         :order       23}
          :organization {:displayName "Organization"
                         :category    "general"
                         :description "user's organization"
                         :type        "string"
                         :mandatory   false
                         :readOnly    false
                         :order       24}}))


;;
;; initialization: register this user template and create direct registration template
;;

(defn initialize
  []
  (p/register registration-method desc)
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
