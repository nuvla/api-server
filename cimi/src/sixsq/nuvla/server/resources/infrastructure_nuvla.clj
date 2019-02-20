(ns sixsq.nuvla.server.resources.infrastructure-nuvla
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure :as p]
    [sixsq.nuvla.server.resources.infrastructure-template-nuvla :as tpl]
    [sixsq.nuvla.server.resources.spec.infrastructure-nuvla :as nuvla]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nuvla/schema))
(defmethod p/validate-subtype tpl/infra-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::nuvla/schema-create))
(defmethod p/create-validate-subtype tpl/infra-type
  [resource]
  (create-validate-fn resource))


;;;
;;; multimethod for edition
;;;
;(defmethod p/special-edit tpl/credential-type
;  [resource request]
;  (if ((:user-roles request) "ADMIN")
;    resource
;    (dissoc resource :claims)))

;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::nuvla/schema))
