(ns sixsq.nuvla.server.resources.user-template-minimum
  "
Template that allows an administrator to register a user with a minimum of
information. This template is intended to be used only by administrators for
creating new accounts without email addresses or email address validation.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-minimum :as spec-minimum]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "minimum")


(def ^:const resource-name "Registration with minimum information")


(def ^:const resource-url registration-method)


(def resource-acl {:owners ["group/nuvla-admin"]})


;;
;; resource
;;

(def ^:const resource
  {:method            registration-method
   :instance          registration-method
   :name              "Registration with minimum information"
   :description       "allows user registration with only the minimum information"
   :resource-metadata (str "resource-metadata/" p/resource-type "-" registration-method)
   :order             0
   :icon              "user"
   :acl               resource-acl})


;;
;; initialization: register this user template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::spec-minimum/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::spec-minimum/schema-create "create"))


(defn initialize
  []
  (p/register registration-method)
  (std-crud/initialize p/resource-type ::spec-minimum/schema)

  (md/register resource-metadata)
  (md/register resource-metadata-create)

  (std-crud/add-if-absent (str p/resource-type "/" registration-method) p/resource-type resource))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::spec-minimum/schema))


(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
