(ns sixsq.nuvla.server.resources.job.utils
  (:require
    [clj-time.core :as time]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.zookeeper :as uzk]))

(def state-running "RUNNING")
(def state-failed "FAILED")
(def state-stopping "STOPPING")
(def state-stopped "STOPPED")
(def state-success "SUCCESS")
(def state-queued "QUEUED")

(def kazoo-queue-prefix "entry-")
(def job-base-node "/job")
(def locking-queue "/entries")
(def locking-queue-path (str job-base-node locking-queue))


(defn stop
  [{state :state id :id :as job}]
  (if (= state state-running)
    (do
      (log/warn "Stopping job : " id)
      (assoc job :state state-stopped))
    job))


(defn add-job-to-queue
  [job-id priority]
  (uzk/create (str locking-queue-path "/" kazoo-queue-prefix (format "%03d" priority) "-")
              :data (uzk/string-to-byte job-id) :sequential? true :persistent? true))


(defn create-job-queue
  []
  (when-not (uzk/exists locking-queue-path)
    (uzk/create-all locking-queue-path :persistent? true)))


(defn is-final-state?
  [{:keys [state] :as job}]
  (contains? #{state-failed state-success} state))


(defn add-targetResource-in-affectedResources
  [{:keys [targetResource affectedResources] :as job}]
  (assoc job :affectedResources (conj affectedResources targetResource)))


(defn should_insert_targetResource-in-affectedResources?
  [{:keys [targetResource affectedResources] :as job}]
  (when targetResource
    (not-any? #(= targetResource %) affectedResources)))


(defn update-timeOfStatusChange
  [job]
  (assoc job :timeOfStatusChange (u/unparse-timestamp-datetime (time/now))))


(defn job-cond->addition
  [{:keys [progress statusMessage] :as job}]
  (cond-> job
          statusMessage update-timeOfStatusChange
          (not progress) (assoc :progress 0)
          (should_insert_targetResource-in-affectedResources? job) add-targetResource-in-affectedResources))


(defn job-cond->edition
  [{:keys [statusMessage state started] :as job}]
  (cond-> job
          (and (not started)
               (= state state-running)) (assoc :started (u/unparse-timestamp-datetime (time/now)))
          true (dissoc :priority)
          statusMessage (update-timeOfStatusChange)
          (is-final-state? job) (assoc :progress 100
                                       :duration (some-> started
                                                         (u/as-datetime)
                                                         (time/interval (time/now))
                                                         (time/in-seconds)))))
