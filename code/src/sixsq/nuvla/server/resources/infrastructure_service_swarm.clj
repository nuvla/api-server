(ns sixsq.nuvla.server.resources.infrastructure-service-swarm
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
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
  [service request]
  (try
    (let [id (:id service)
          user-id (:identity (a/current-authentication request))
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "start_infrastructure_service_swarm"
                                                       {:owner {:principal "ADMIN"
                                                                :type      "ROLE"}
                                                        :rules [{:principal user-id
                                                                 :right     "VIEW"
                                                                 :type      "USER"}]}
                                                       :priority 50)
          job-msg (str "starting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to start infrastructure service swarm" 500 id)))
      (-> id
          (db/retrieve request)
          (a/can-modify? request)
          (assoc :state "STARTING")
          (db/edit request))
      (event-utils/create-event id job-msg (a/default-acl (a/current-authentication request)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod infra-service/post-delete-hook method
  [service request]
  (try
    (log/info "THIRD")
    (let [body (:body request)
          user-id (:identity (a/current-authentication request))
          id (:resource-id body)
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "stope_infrastructure_service_swarm"
                                                       {:owner {:principal "ADMIN"
                                                                :type      "ROLE"}
                                                        :rules [{:principal user-id
                                                                 :right     "VIEW"
                                                                 :type      "USER"}]}
                                                       :priority 50)
          job-msg (str "stopping " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to stop infrastructure service swarm" 500 id)))
      (-> id
          (db/retrieve request)
          (a/can-modify? request)
          (assoc :state "STOPPING")
          (db/edit request))
      (event-utils/create-event id job-msg (a/default-acl (a/current-authentication request)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))
