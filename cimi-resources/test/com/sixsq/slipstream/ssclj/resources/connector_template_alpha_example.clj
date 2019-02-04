(ns com.sixsq.slipstream.ssclj.resources.connector-template-alpha-example
  "This is an example ConnectorTemplate resource that shows how a
   concrete ConnectorTemplate resource would be defined and also to
   provide a concrete resource for testing."
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.connector-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.connector-template]
    [com.sixsq.slipstream.ssclj.resources.spec.connector-template :as ps]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(def ^:const cloud-service-type "alpha")

;;
;; schemas
;;

(s/def :cimi.connector-template.alpha/alphaKey pos-int?)
(s/def :cimi.connector-template.alpha/objectStoreEndpoint ::ps/objectStoreEndpoint)

;; Defines the contents of the alpha ConnectorTemplate resource itself.
(s/def :cimi/connector-template.alpha
  (su/only-keys-maps ps/resource-keys-spec
                     {:req-un [:cimi.connector-template.alpha/alphaKey]
                      :opt-un [:cimi.connector-template.alpha/objectStoreEndpoint]}))

;; Defines the contents of the alpha template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :connectorTemplate here.
(s/def :cimi.connector-template.alpha/connectorTemplate
  (su/only-keys-maps ps/template-keys-spec
                     {:opt-un [:cimi.connector-template.alpha/alphaKey
                               :cimi.connector-template.alpha/objectStoreEndpoint]}))

(s/def :cimi/connector-template.alpha-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [:cimi.connector-template.alpha/connectorTemplate]}))

(def ConnectorTemplateAlphaDescription
  (merge p/ConnectorTemplateDescription
         {:alphaKey {:displayName "Alpha Key"
                     :category    "general"
                     :description "example parameter"
                     :type        "int"
                     :mandatory   true
                     :readOnly    false
                     :order       1}}))
;;
;; resource
;;
(def ^:const resource
  {:cloudServiceType cloud-service-type
   :alphaKey         1001})

;;
;; description
;;
(def ^:const desc ConnectorTemplateAlphaDescription)

;;
;; initialization: register this connector template
;;
(defn initialize
  []
  (p/register resource desc))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/connector-template.alpha))
(defmethod p/validate-subtype cloud-service-type
  [resource]
  (validate-fn resource))
