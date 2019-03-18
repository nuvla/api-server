(ns sixsq.nuvla.server.resources.infrastructure-service-swarm
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-template-swarm :as tpl]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm :as tpl-swarm]))


(def ^:const method "swarm")

;;
;; initialization
;;

(defn initialize
      []
      (std-crud/initialize infra-service/resource-type ::tpl-swarm/schema))

;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema-create))
(def validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema))


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
;; multimethods for validation
;;

;(def validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema))
;(defmethod infra-service/validate-subtype tpl/method
;           [resource]
;           (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema-create))
(defmethod infra-service/create-validate-subtype tpl/method
           [resource]
           (create-validate-fn resource))


;;
;; post-add hook that creates a job that will deploy a swarm
;;

(defmethod infra-service/post-add-hook method
  [service template]
  (log/error "SWARM POST ADD HOOK:\n"
             (with-out-str (clojure.pprint/pprint service)) "\n"
             (with-out-str (clojure.pprint/pprint template)))
  nil)
