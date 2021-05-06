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
    [sixsq.nuvla.server.resources.notification.utils :as notif-utils]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-coe :as infra-service-coe]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-coe :as tpl-coe]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const method "coe")


;;
;; Utils
;;

(defn throw-can-not-do-action
  [{:keys [id state] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "invalid state (%s) for %s on %s" state action id) 409 id))))


(defn can-start?
  [{:keys [state] :as _resource}]
  (#{"STOPPED"} state))


(defn can-stop?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED"} state))


(defn can-terminate?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED" "STOPPED" "ERROR"} state))


(defn can-delete?
  [{:keys [state] :as _resource}]
  (#{"TERMINATED"} state))


(defn queue-mgt-job
  "Creates and queues the named service management job for the given service
   id and user. Returns the `:body` of the job creation response."
  [id user-id job-name]
  (let [acl {:owners   ["group/nuvla-admin"]
             :view-acl [user-id]}]
    (:body (job/create-job id job-name acl :priority 50))))


(defn edit-infra-service
  [resource request edit-fn]
  (-> resource
      (edit-fn)
      (u/update-timestamps)
      (u/set-updated-by request)
      (db/edit request)))


(defn create-job
  "Starts the given job and updates the state of the resource."
  [resource request job-name new-state]
  (try
    (let [resource-id (:id resource)
          {job-id :resource-id status :status} (queue-mgt-job
                                                 resource-id
                                                 (auth/current-active-claim request)
                                                 job-name)]
      (if (= 201 status)
        (let [job-msg (format "created job %s with id %s" job-name job-id)]
          (edit-infra-service resource request #(assoc % :state new-state))
          (infra-service/event-state-change resource (assoc-in request [:body :state] new-state))
          (event-utils/create-event resource-id job-msg (a/default-acl (auth/current-authentication request)))
          (r/map-response job-msg 202 resource-id job-id))
        (throw (r/ex-response (format "unable to create job %s" job-name) 500 resource-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; CRUD operations
;;

(defn remove-delete
  [operations]
  (vec (remove #(= (name :delete) (:rel %)) operations)))


(defmethod infra-service/set-crud-operations method
  [{id :id :as resource} request]
  (let [start-op     (u/action-map id :start)
        stop-op      (u/action-map id :stop)
        terminate-op (u/action-map id :terminate)
        can-manage?  (a/can-manage? resource request)]
    (cond-> (crud/set-standard-resource-operations resource request)
            (and can-manage? (not (can-delete? resource))) (update :operations remove-delete)
            (and can-manage? (can-start? resource)) (update :operations conj start-op)
            (and can-manage? (can-stop? resource)) (update :operations conj stop-op)
            (and can-manage? (can-terminate? resource)) (update :operations conj terminate-op))))


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
      (throw (r/ex-response (format ":management-credential required to create COE %s" (:subtype service))
                            412 (:id service))))
    (let [id           (:id service)
          coe-type     (:subtype service)
          active-claim (auth/current-active-claim request)
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "provision_infrastructure_service_coe"
                                                       {:owners   ["group/nuvla-admin"]
                                                        :view-acl [active-claim]}
                                                       :priority 50)
          job-msg      (str "starting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response (str "unable to create async job to start infrastructure service " coe-type) 500 id)))
      (-> id
          (db/retrieve request)
          (a/throw-cannot-edit request)
          (assoc :state "STARTING")
          (u/update-timestamps)
          (u/set-updated-by request)
          (db/edit request))
      (notif-utils/create-state-event-notification-subscription id request)
      (event-utils/create-event id "STARTING" (a/default-acl (auth/current-authentication request))
                                :severity "low"
                                :category "state")
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; Delete resource itself only if it's TERMINATED.
(defmethod infra-service/delete "coe"
  [resource request]
  (throw-can-not-do-action resource can-delete? "delete")
  (infra-service/delete-impl request))


;; Stop IS COE on CSP using job.
(defmethod infra-service/do-action-stop method
  [resource request]
  (try
    (-> resource
        (throw-can-not-do-action can-stop? "stop")
        (create-job request "stop_infrastructure_service_coe" "STOPPING"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; Start IS COE on CSP using job.
(defmethod infra-service/do-action-start method
  [resource request]
  (try
    (-> resource
        (throw-can-not-do-action can-start? "start")
        (create-job request "start_infrastructure_service_coe" "STARTING"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; Terminate IS COE on CSP using job.
(defmethod infra-service/do-action-terminate method
  [resource request]
  (try
    (-> resource
        (throw-can-not-do-action can-terminate? "terminate")
        (create-job request "terminate_infrastructure_service_coe" "TERMINATING"))
    (catch Exception e
      (or (ex-data e) (throw e)))))

