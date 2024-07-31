(ns com.sixsq.nuvla.server.resources.job.utils
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as time]
    [com.sixsq.nuvla.server.util.general :as util-general]
    [com.sixsq.nuvla.server.util.zookeeper :as uzk]))

(def state-queued "QUEUED")
(def state-running "RUNNING")
(def state-failed "FAILED")
(def state-canceled "CANCELED")
(def state-success "SUCCESS")

(def states #{state-queued state-running state-failed state-canceled state-success})

(def final-states #{state-failed state-success state-canceled})

(def action-cancel "cancel")
(def action-get-context "get-context")
(def action-timeout "timeout")

(def kazoo-queue-prefix "entry-")
(def job-base-node "/job")
(def locking-queue "/entries")
(def locking-queue-path (str job-base-node locking-queue))

(defn can-cancel?
  [{:keys [state] :as resource} request]
  (and (boolean (#{state-queued state-running} state))
       (a/can-manage? resource request)))

(defn throw-cannot-cancel
  [resource request]
  (if (can-cancel? resource request)
    resource
    (throw (r/ex-unauthorized (:id resource)))))

(defn can-timeout?
  [{:keys [state] :as resource} request]
  (and (boolean (#{state-running} state))
       (a/can-manage? resource request)
       (a/is-admin-request? request)))

(defn throw-cannot-timeout
  [resource request]
  (if (can-timeout? resource request)
    resource
    (throw (r/ex-unauthorized (:id resource)))))

(defn add-job-to-queue
  [job-id priority]
  (uzk/create (str locking-queue-path "/" kazoo-queue-prefix (format "%03d" priority) "-")
              :data (uzk/string-to-byte job-id) :sequential? true :persistent? true))

(defn create-job-queue
  []
  (when-not (uzk/exists locking-queue-path)
    (uzk/create-all locking-queue-path :persistent? true)))

(defn is-final-state?
  [{:keys [state] :as _job}]
  (contains? final-states state))

(defn update-time-of-status-change
  [job]
  (assoc job :time-of-status-change (time/now-str)))

(defn truncate-status-message
  [job]
  (update job :status-message util-general/truncate 50000))

(defn job-cond->addition
  [{:keys [target-resource affected-resources progress status-message] :as job}]
  (cond-> job
          status-message (-> update-time-of-status-change
                             truncate-status-message)
          (not progress) (assoc :progress 0)
          (and target-resource
               (not-any?
                 #(= target-resource %)
                 affected-resources)) (assoc :affected-resources
                                             (conj affected-resources
                                                   target-resource))))

(defn job-cond->edition
  [{:keys [status-message state started] :as job}]
  (let [job-in-final-state? (is-final-state? job)]
    (cond-> (dissoc job :priority)
            (and (not started)
                 (= state state-running)) (assoc :started (time/now-str))
            status-message (-> update-time-of-status-change
                               truncate-status-message)

            job-in-final-state? (assoc :progress 100)
            (and job-in-final-state?
                 started) (assoc :duration (time/time-between-date-now started :seconds)))))

(defn can-get-context?
  [resource request]
  (let [active-claim (auth/current-active-claim request)]
    (or (and (a/can-manage? resource request)
             (str/starts-with? active-claim "nuvlabox/"))
        (a/is-admin-request? request))))

(defn throw-cannot-get-context
  [resource request]
  (if (can-get-context? resource request)
    resource
    (throw (r/ex-unauthorized (:id resource)))))

(defn throw-cannot-edit-in-final-state
  [{:keys [id] :as job}]
  (if (is-final-state? job)
    (throw (r/ex-response "edit is not allowed in final state" 409 id))
    job))
