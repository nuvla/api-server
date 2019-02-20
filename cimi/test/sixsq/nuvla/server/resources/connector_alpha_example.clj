(ns sixsq.nuvla.server.resources.connector-alpha-example
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector :as p]
    [sixsq.nuvla.server.resources.connector-template-alpha-example :as tpl]))

(def ^:const cloud-service-type "alpha")

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl/schema))
(defmethod p/validate-subtype cloud-service-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::tpl/schema-create))
(defmethod p/create-validate-subtype cloud-service-type
  [resource]
  (create-validate-fn resource))
