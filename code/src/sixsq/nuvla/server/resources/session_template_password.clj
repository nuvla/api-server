(ns sixsq.nuvla.server.resources.session-template-password
  "
Resource that is used to create a session using a username and password for
credentials. This template is guaranteed to be present on all server instances.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.session-template :as p]
    [sixsq.nuvla.server.resources.spec.session-template-password :as st-password]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "password")


(def ^:const resource-name "Password")


(def ^:const resource-url authn-method)


(def default-template {:method            authn-method
                       :instance          authn-method
                       :name              "Password"
                       :description       "Password Authentication via Username/Password"
                       :resource-metadata (str "resource-metadata/" p/resource-type "-" authn-method)
                       :group             "Login with Username/Password"
                       :username          "username"
                       :password          "password"
                       :order             0
                       :icon              "user"
                       :acl               p/resource-acl})


;;
;; initialization: register this Session template and create password authentication template
;;

(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-type ::st-password/schema)
  (std-crud/add-if-absent (str "session-template/" authn-method) p/resource-type default-template)

  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-password/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-password/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
