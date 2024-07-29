(ns com.sixsq.nuvla.server.resources.user-template-minimum
  "
Template that allows an administrator to register a user with a minimum of
information. This template is intended to be used only by administrators for
creating new accounts without email addresses or email address validation.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.user-template-minimum :as spec-minimum]
    [com.sixsq.nuvla.server.resources.user-template :as user-tpl]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


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
   :resource-metadata (str "resource-metadata/" user-tpl/resource-type "-" registration-method)
   :order             0
   :icon              "user"
   :acl               resource-acl})


;;
;; initialization: register this user template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::user-tpl/ns ::spec-minimum/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::user-tpl/ns ::spec-minimum/schema-create "create"))


(defn add-min-reg-resource
  []
  (std-crud/add-if-absent
    (str user-tpl/resource-type "/" registration-method)
    user-tpl/resource-type
    resource))


(defn initialize-data
  []
  (add-min-reg-resource))


(defn initialize
  []
  (user-tpl/register registration-method)
  (std-crud/initialize user-tpl/resource-type ::spec-minimum/schema)

  (md/register resource-metadata)
  (md/register resource-metadata-create)

  (initialize-data))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::spec-minimum/schema))


(defmethod user-tpl/validate-subtype registration-method
  [resource]
  (validate-fn resource))
