(ns sixsq.nuvla.server.resources.infrastructure-service-generic
  "
A generic infrastructure service that is characterized by a service type and
an endpoint.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic :as infra-service-tpl-generic]))


(def ^:const method "generic")


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::infra-service-tpl-generic/schema-create))


(defmethod infra-service/create-validate-subtype method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into service resource
;;

(defmethod infra-service/tpl->service method
  [{:keys [state] :as resource}]
  (cond-> (dissoc resource :href :resource-metadata)
          (nil? state) (assoc :state "STARTED")))

;;
;; Explicitely let to delete the generic service resource even if the resource state
;; doesn't permit this. See CAN_DELETE_STATES in infra-service.
;;
(defmethod infra-service/delete-hook method
  [service request]
  (infra-service/delete-impl request))
