(ns sixsq.nuvla.server.resources.infrastructure-service-coe
  "
Information concerning a COE cluster and the parameters necessary to
manage it.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-coe :as infra-service-coe]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-coe :as tpl-coe]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const method "coe")

;;
;; multimethods for validation based on COE subtypes
;;

(def validate-fn (u/create-spec-validation-fn ::infra-service-coe/schema))


(defmethod infra-service/validate-subtype "kubernetes"
  [resource]
  (validate-fn resource))


(defmethod infra-service/validate-subtype "swarm"
  [resource]
  (validate-fn resource))


;;
;; multimethods for create request validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::tpl-coe/schema-create))


(defmethod infra-service/create-validate-subtype method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into service resource
;;

(defmethod infra-service/tpl->service method
  [resource]
  (-> resource
      (dissoc resource :href :resource-metadata :endpoint)
      (assoc :state "CREATED")))


;;
;; post-add hook that creates a job that will deploy Container Orchestration Engine
;; corresponding to :subtype of the infrastructure service.
;;

(defmethod infra-service/post-add-hook method
  [service request]
  (try
    (when-not (contains? service :management-credential)
      (throw (r/ex-response (format ":management-credential required to create COE %" (:subtype service))
                            412 (:id service))))
    (let [id       (:id service)
          coe-type (:subtype service)
          active-claim  (auth/current-active-claim request)
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "start_infrastructure_service_coe"
                                                       {:owners   ["group/nuvla-admin"]
                                                        :view-acl [active-claim]}
                                                       :priority 50)
          job-msg (str "starting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response (str "unable to create async job to start infrastructure service " coe-type) 500 id)))
      (-> id
          (db/retrieve request)
          (a/throw-cannot-edit request)
          (assoc :state "STARTING")
          (u/update-timestamps)
          (u/set-updated-by request)
          (db/edit request))
      (event-utils/create-event id job-msg (a/default-acl (auth/current-authentication request)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; COE delete hook that deletes the cluster from cloud.

(defn queue-mgt-job
  "Creates and queues the named service management job for the given service
   id and user. Returns the `:body` of the job creation response."
  [id user-id job-name]
  (let [acl {:owners   ["group/nuvla-admin"]
             :view-acl [user-id]}]
    (:body (job/create-job id job-name acl :priority 50))))

(defn update-job-state
  [service-id state]
  (-> (crud/retrieve-by-id-as-admin service-id)
      (u/update-timestamps)
      (assoc :state state)
      #_(db/edit admin-opts)))

(defn job-hook
  "Starts the given job and updates the state of the resource."
  [service-id request job-name new-state]
  (try
    (let [user-id (auth/current-active-claim request)
          {:keys [resource-id status]} (queue-mgt-job service-id user-id job-name)]
      (if (= 201 status)
        (let [job-msg (format "created job %s with id %s" job-name resource-id)]
          (update-job-state service-id new-state)
          (event-utils/create-event service-id job-msg (a/default-acl (auth/current-authentication request)))
          (r/map-response job-msg 202 service-id resource-id))
        (throw (r/ex-response (format "unable to create job %s" job-name) 500 service-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;; Terminate IS COE from CSP using job.
(defmethod infra-service/delete-hook "coe"
  [service request]
  (job-hook (:id service) request "stop_infrastructure_service_coe" "STOPPING"))


