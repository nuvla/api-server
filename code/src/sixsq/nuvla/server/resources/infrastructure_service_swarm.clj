(ns sixsq.nuvla.server.resources.infrastructure-service-swarm
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service.utils :as is-utils]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm :as tpl-swarm]))


(def ^:const method "swarm")


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema-create))


(defmethod infra-service/create-validate-subtype method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into service resource
;;

(defmethod infra-service/tpl->service method
  [{{:keys [href]} :service-credential :as resource}]
  (-> resource
      (dissoc resource :href :resourceMetadata :endpoint :service-credential)
      (assoc :state "CREATED"
             :management-credential-id href)))


;;
;; post-add hook that creates a job that will deploy a swarm
;;

(defmethod infra-service/post-add-hook method
  [{:keys [id] :as service} request]
  (is-utils/job-hook id request "start_infrastructure_service_swarm" "STARTING"))


(defmethod infra-service/post-delete-hook method
  [{:keys [id] :as service} request]
  (is-utils/job-hook id request "stop_infrastructure_service_swarm" "STOPPING"))
