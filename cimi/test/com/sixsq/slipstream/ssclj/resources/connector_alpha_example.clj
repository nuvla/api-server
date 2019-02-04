(ns com.sixsq.slipstream.ssclj.resources.connector-alpha-example
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.connector :as p]
    [com.sixsq.slipstream.ssclj.resources.connector-template-alpha-example :as tpl]))

(def ^:const cloud-service-type "alpha")

;;
;; schemas
;;

;; Trivial example has the same schema as the template.  A real
;; resource may have different schemas for the template and resource.
(s/def :cimi/connector.alpha :cimi/connector-template.alpha)

(def ConnectorAlphaDescription
  tpl/ConnectorTemplateAlphaDescription)

;;
;; description
;;
(def ^:const desc ConnectorAlphaDescription)

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/connector.alpha))
(defmethod p/validate-subtype cloud-service-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn :cimi/connector-template.alpha-create))
(defmethod p/create-validate-subtype cloud-service-type
  [resource]
  (create-validate-fn resource))
