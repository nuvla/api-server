(ns sixsq.nuvla.server.resources.external-object-generic
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.external-object :as eo]
    [sixsq.nuvla.server.resources.external-object-template-generic :as eot]
    [sixsq.nuvla.server.resources.spec.external-object-generic :as eo-generic]))

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
  (std-crud/initialize eo/resource-type ::eo-generic/external-object))

