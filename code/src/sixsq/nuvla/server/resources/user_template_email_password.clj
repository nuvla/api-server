(ns sixsq.nuvla.server.resources.user-template-email-password
  "
Template that allows a user to register with an email address and password. An
optional name can also be provided.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-email-password :as spec-email-password]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "email-password")


(def ^:const resource-name "Registration with Email and Password")


(def ^:const resource-url registration-method)


(def resource-acl {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-anon"]})

;;
;; resource
;;

(def ^:const resource
  {:method           registration-method
   :instance         registration-method
   :name             "Registration with Email and Password"
   :description      "allows user registration with email, password, and optional username"
   :resourceMetadata (str "resource-metadata/" p/resource-type "-" registration-method)
   :order            0
   :icon             "user"
   :acl              resource-acl})


;;
;; initialization: register this user template
;;

(defn initialize
  []
  (p/register registration-method)
  (std-crud/initialize p/resource-type ::spec-email-password/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::spec-email-password/schema))
  (std-crud/add-if-absent (str p/resource-type "/" registration-method) p/resource-type resource))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::spec-email-password/schema))
(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
