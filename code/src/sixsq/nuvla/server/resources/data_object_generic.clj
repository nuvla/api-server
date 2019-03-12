(ns sixsq.nuvla.server.resources.data-object-generic
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template-generic :as dot]
    [sixsq.nuvla.server.resources.spec.data-object-generic :as do-generic]))

;; multimethods for validation

(def validate-fn (u/create-spec-validation-fn ::do-generic/data-object))
(defmethod do/validate-subtype dot/type
  [resource]
  (validate-fn resource))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize do/resource-type ::do-generic/data-object))

