(ns com.sixsq.nuvla.server.resources.user-template-username-password
  "
Template that allows a user to register with a username (identifier) and
password. This template is intended to be used only by administrators for
creating new accounts without email addresses or email address validation.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.user-template-username-password :as spec-username-password]
    [com.sixsq.nuvla.server.resources.user-template :as p]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "username-password")


(def ^:const resource-name "Registration with Username and Password")


(def ^:const resource-url registration-method)


(def resource-acl {:owners ["group/nuvla-admin"]})


;;
;; resource
;;

(def ^:const resource
  {:method            registration-method
   :instance          registration-method
   :name              "Registration with Username and Password"
   :description       "allows user registration with a username and password"
   :resource-metadata (str "resource-metadata/" p/resource-type "-" registration-method)
   :order             0
   :icon              "user"
   :acl               resource-acl})


;;
;; initialization: register this user template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::spec-username-password/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::spec-username-password/schema-create "create"))


(defn add-reg-method []
  (std-crud/add-if-absent
    (str p/resource-type "/" registration-method)
    p/resource-type
    resource))


(defn initialize-data
  []
  (add-reg-method))

(def initialization-order 20)

(defn initialize
  []
  (p/register registration-method)
  (std-crud/initialize p/resource-type ::spec-username-password/schema)

  (md/register resource-metadata)
  (md/register resource-metadata-create)

  (initialize-data))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::spec-username-password/schema))


(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
