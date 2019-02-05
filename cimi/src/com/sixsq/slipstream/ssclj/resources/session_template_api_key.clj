(ns com.sixsq.slipstream.ssclj.resources.session-template-api-key
  "
Resource that is used to create a session from the provided API key-secret
pair.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.session-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template-api-key :as st-api-key]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const authn-method "api-key")


(def ^:const resource-name "API Key")


(def ^:const resource-url authn-method)

(def default-template {:method      authn-method
                       :instance    authn-method
                       :name        "API Key"
                       :description "Authentication with API Key and Secret"
                       :username    "key"
                       :password    "secret"
                       :acl         p/resource-acl})


;;
;; initialization: register this Session template
;;
(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-url ::st-api-key/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-api-key/schema)))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-api-key/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
