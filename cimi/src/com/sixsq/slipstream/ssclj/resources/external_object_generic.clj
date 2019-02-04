(ns com.sixsq.slipstream.ssclj.resources.external-object-generic
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-generic :as eot]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-generic :as eo-generic]))

;; multimethods for validation

(def validate-fn (u/create-spec-validation-fn ::eo-generic/external-object))
(defmethod eo/validate-subtype eot/objectType
  [resource]
  (validate-fn resource))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize eo/resource-url ::eo-generic/external-object))

