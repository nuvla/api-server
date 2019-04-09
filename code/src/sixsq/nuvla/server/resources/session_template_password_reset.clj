(ns sixsq.nuvla.server.resources.session-template-password-reset
  "
Resource that is used to reset password for a username and to create a session at the end of
the process. This template is guaranteed to be present on all server instances.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.session-template :as p]
    [sixsq.nuvla.server.resources.spec.session-template-password-reset :as st-password-reset]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "password-reset")


(def ^:const resource-name "Password reset")


(def ^:const resource-url authn-method)


(def default-template {:method            authn-method
                       :instance          authn-method
                       :name              "Password reset"
                       :description       "Reset forgoten password via a Username"
                       :resource-metadata (str "resource-metadata/" p/resource-type "-" authn-method)
                       :group             "Login with Username/Password"
                       :username          "username"
                       :new-password      "new-password"
                       :order             30
                       :hidden            true
                       :icon              "ambulance"
                       :acl               p/resource-acl})


;;
;; initialization: register this Session template and create password reset authentication template
;;

(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-type ::st-password-reset/schema)
  (std-crud/add-if-absent (str "session-template/" authn-method) p/resource-type default-template)

  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-password-reset/schema))
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-password-reset/schema-create "create")))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-password-reset/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
