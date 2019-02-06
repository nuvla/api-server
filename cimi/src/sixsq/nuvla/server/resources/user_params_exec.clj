(ns sixsq.nuvla.server.resources.user-params-exec
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.user-params-template-exec]
    [sixsq.nuvla.server.resources.user-params :as p]
    [sixsq.nuvla.server.resources.user-params-template-exec :as tpl]))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/user-params-template.exec))
(defmethod p/validate-subtype tpl/params-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn :cimi/user-params-template.exec-create))
(defmethod p/create-validate-subtype tpl/params-type
  [resource]
  (create-validate-fn resource))

;;
;; transform template into user params esource

(defmethod p/tpl->user-params tpl/params-type
  [resource request]
  (assoc resource :resourceURI p/resource-uri))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-url :cimi/user-params-template.exec))
