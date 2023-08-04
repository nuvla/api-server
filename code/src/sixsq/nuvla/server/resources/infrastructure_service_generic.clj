(ns sixsq.nuvla.server.resources.infrastructure-service-generic
  "
A generic infrastructure service that is characterized by a service type and
an endpoint.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.eventing :as eventing]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
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


(defmethod infra-service/post-add-hook method
  [service request]
  (try
    (let [id       (:id service)
          category "state"]
      (eventing/create-event*
        request
        id
        {:event-type "infrastructure-service.state.changed"
         :details    {:new-state (:state service)}})
      #_(event-utils/create-event-old id
                                      ((keyword category) service)
                                      (a/default-acl (auth/current-authentication request))
                                      :severity "low"
                                      :category category))
    (catch Exception e
      (or (ex-data e) (throw e)))))
