(ns com.sixsq.nuvla.server.resources.infrastructure-service-vpn
  "
Information concerning a Docker Swarm cluster and the parameters necessary to
manage it.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service-template-vpn :as tpl-vpn]))


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
