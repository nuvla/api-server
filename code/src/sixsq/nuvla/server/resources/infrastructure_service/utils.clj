(ns sixsq.nuvla.server.resources.infrastructure-service.utils
  (:require
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]))


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})


(defn verify-can-delete
  [{:keys [id state] :as resource}]
  (if (#{"CREATED" "STOPPED"} state)
    resource
    (throw (r/ex-response (str "invalid state (" state ") for delete on " id) 412 id ))))


(defn queue-mgt-job
  "Creates and queues the named service management job for the given service
   id and user. Returns the `:body` of the job creation response."
  [id user-id job-name]
  (let [acl {:owner {:principal "ADMIN"
                     :type      "ROLE"}
             :rules [{:principal user-id
                      :right     "VIEW"
                      :type      "USER"}]}]
    (:body (job/create-job id job-name acl :priority 50))))


(defn update-job-state
  [service-id state]
  (-> (crud/retrieve-by-id-as-admin service-id)
      (u/update-timestamps)
      (assoc :state state)
      (db/edit admin-opts)))


(defn job-hook
  "Starts the given job and updates the state of the resource."
  [service-id request job-name new-state]
  (try
    (let [user-id (:identity (a/current-authentication request))
          {:keys [resource-id status]} (queue-mgt-job service-id user-id job-name)]

      (if (= 201 status)
        (let [job-msg (format "created job %s with id %s" job-name resource-id)]
          (update-job-state service-id new-state)
          (event-utils/create-event service-id job-msg (a/default-acl (a/current-authentication request)))
          (r/map-response job-msg 202 service-id resource-id))
        (throw (r/ex-response (format "unable to create job %s" job-name) 500 service-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))
