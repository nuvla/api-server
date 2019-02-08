(ns sixsq.nuvla.server.resources.session-template-internal
  "
Resource that is used to create a session using a username and password for
credentials. This template is guaranteed to be present on all server instances.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.session-template :as p]
    [sixsq.nuvla.server.resources.spec.session-template-internal :as st-internal]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "internal")


(def ^:const resource-name "Internal")


(def ^:const resource-url authn-method)


(def default-template {:method           authn-method
                       :instance         authn-method
                       :name             "Internal"
                       :description      "Internal Authentication via Username/Password"
                       :resourceMetadata (str p/resource-type "-" authn-method)
                       :group            "Login with Username/Password"
                       :username         "username"
                       :password         "password"
                       :acl              p/resource-acl})


;;
;; initialization: register this Session template and create internal authentication template
;;

(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-type ::st-internal/schema)
  (std-crud/add-if-absent (str "session-template/" authn-method) p/resource-type default-template)

  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-internal/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-internal/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
