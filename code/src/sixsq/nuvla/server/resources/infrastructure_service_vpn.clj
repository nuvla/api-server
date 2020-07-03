(ns sixsq.nuvla.server.resources.infrastructure-service-vpn
  "
Information concerning a Docker Swarm cluster and the parameters necessary to
manage it.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-vpn :as tpl-vpn]))


(def ^:const subtype "vpn")


(def ^:const method "vpn")


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-vpn/schema-create))

(defmethod infra-service/create-validate-subtype method
  [resource]
  (create-validate-fn resource))

;;
;; multimethods for valide subtype
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-vpn/schema))

(defmethod infra-service/validate-subtype subtype
  [resource]
  (validate-fn resource))


;;
;; transform template into service resource
;;

(defmethod infra-service/tpl->service method
  [resource]
  (dissoc resource :href :resource-metadata))

;;
;; Explicitely let to delete the vpn service resource even if the resource state
;; doesn't permit this. See CAN_DELETE_STATES in infra-service.
;;
(defmethod infra-service/delete-hook method
  [service request]
  (infra-service/delete-impl request))
