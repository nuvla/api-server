(ns sixsq.nuvla.server.resources.service-generic
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.service :as p]
    [sixsq.nuvla.server.resources.spec.service-template-generic :as tpl-generic]))


(def ^:const method "generic")


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-generic/schema-create))


(defmethod p/create-validate-subtype method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into service resource
;;

(defmethod p/tpl->service method
  [{:keys [accessible] :as resource}]
  (cond-> (dissoc resource :href :resourceMetadata)
          (nil? accessible) (assoc :accessible true)))
