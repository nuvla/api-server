(ns sixsq.nuvla.server.resources.session-template-password
  "
Resource that is used to create a session using a username and password for
credentials. This template is guaranteed to be present on all server instances.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.session-template :as session-tpl]
    [sixsq.nuvla.server.resources.spec.session-template-password :as st-password]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "password")


(def ^:const resource-name "Password")


(def ^:const resource-url authn-method)


(def default-template {:method            authn-method
                       :instance          authn-method
                       :name              "Password"
                       :description       "Password Authentication via Username/Password"
                       :resource-metadata (str "resource-metadata/" session-tpl/resource-type "-" authn-method)
                       :group             "Login with Username/Password"
                       :username          "username"
                       :password          "password"
                       :order             0
                       :icon              "user"
                       :acl               session-tpl/resource-acl})


;;
;; initialization: register this Session template and create password authentication template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::session-tpl/ns ::st-password/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::session-tpl/ns ::st-password/schema-create "create"))


(defn add-session-template
  []
  (std-crud/add-if-absent
    (str session-tpl/resource-type "/" authn-method)
    session-tpl/resource-type
    default-template))


(defn initialize-data
  []
  (add-session-template))


(defn initialize
  []
  (session-tpl/register authn-method)
  (std-crud/initialize session-tpl/resource-type ::st-password/schema)
  (initialize-data)

  (md/register resource-metadata)
  (md/register resource-metadata-create))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-password/schema))
(defmethod session-tpl/validate-subtype authn-method
  [resource]
  (validate-fn resource))
