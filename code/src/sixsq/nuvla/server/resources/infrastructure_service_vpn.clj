(ns sixsq.nuvla.server.resources.infrastructure-service-openvpn
  "
Information concerning a Docker Swarm cluster and the parameters necessary to
manage it.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-openvpn :as tpl-openvpn]))


(def ^:const subtype "openvpn")


(def ^:const method "openvpn")


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-openvpn/schema-create))

(defmethod infra-service/create-validate-subtype method
  [resource]
  (create-validate-fn resource))

;;
;; multimethods for valide subtype
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-openvpn/schema))

(defmethod infra-service/validate-subtype subtype
  [resource]
  (validate-fn resource))


;;
;; transform template into service resource
;;

(defmethod infra-service/tpl->service method
  [resource]
  (dissoc resource :href :resource-metadata))

