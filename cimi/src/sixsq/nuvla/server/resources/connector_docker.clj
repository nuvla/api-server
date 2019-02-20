(ns sixsq.nuvla.server.resources.connector-docker
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector :as p]
    [sixsq.nuvla.server.resources.connector-template-docker :as ct-docker]
    [sixsq.nuvla.server.resources.spec.connector-template-docker :as tpl]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl/schema))
(defmethod p/validate-subtype ct-docker/cloud-service-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::tpl/schema-create))
(defmethod p/create-validate-subtype ct-docker/cloud-service-type
  [resource]
  (create-validate-fn resource))
