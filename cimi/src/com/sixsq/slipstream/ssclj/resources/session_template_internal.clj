(ns com.sixsq.slipstream.ssclj.resources.session-template-internal
  "
Resource that is used to create a session using a username and password for
credentials. This template is guaranteed to be present on all server instances.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.session-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template-internal :as st-internal]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const authn-method "internal")


(def ^:const resource-name "Internal")


(def ^:const resource-url authn-method)


(def default-template {:method           authn-method
                       :instance         authn-method
                       :name             "Internal"
                       :description      "Internal Authentication via Username/Password"
                       :resourceMetadata (str p/resource-url "-" authn-method)
                       :group            "Login with Username/Password"
                       :username         "username"
                       :password         "password"
                       :acl              p/resource-acl})


;;
;; description
;;

(def ^:const desc
  (merge p/SessionTemplateDescription
         {:username {:displayName  "Username"
                     :category     "general"
                     :description  "username"
                     :type         "string"
                     :mandatory    true
                     :readOnly     false
                     :order        20
                     :autocomplete "username"}
          :password {:displayName  "Password"
                     :category     "general"
                     :description  "password"
                     :type         "password"
                     :mandatory    true
                     :readOnly     false
                     :order        21
                     :autocomplete "password"}}))


;;
;; initialization: register this Session template and create internal authentication template
;;

(defn initialize
  []
  (p/register authn-method desc)
  (std-crud/initialize p/resource-url ::st-internal/schema)
  (std-crud/add-if-absent (str "session-template/" authn-method) p/resource-url default-template)

  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-internal/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-internal/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
