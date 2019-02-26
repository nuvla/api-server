(ns sixsq.nuvla.server.resources.infrastructure-service-swarm
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm :as tpl-swarm]
    [clojure.tools.logging :as log]))


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
  [resource]
  (-> resource
      (dissoc resource :href :resourceMetadata :endpoint :cloud-service :service-credential)
      (assoc :state "CREATED")))


;;
;; post-add hook that creates a job that will deploy a swarm
;;

(defmethod infra-service/post-add-hook method
  [service template]
  (log/error "SWARM POST ADD HOOK:\n"
             (with-out-str (clojure.pprint/pprint service)) "\n"
             (with-out-str (clojure.pprint/pprint template)))
  nil)
