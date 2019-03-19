(ns sixsq.nuvla.server.resources.infrastructure-service-kubernetes
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-kubernetes :as tpl-kubernetes]))


(def ^:const method "kubernetes")


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-kubernetes/schema-create))


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
  [service template]
  (log/error "KUBERNETES POST ADD HOOK:\n"
             (with-out-str (clojure.pprint/pprint service)) "\n"
             (with-out-str (clojure.pprint/pprint template)))
  nil)
