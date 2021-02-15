(ns sixsq.nuvla.server.resources.job.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.util.response :as ru]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]
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


(defn should_insert_target-resource-in-affected-resources?
  [{:keys [target-resource affected-resources] :as job}]
  (when target-resource
    (not-any? #(= target-resource %) affected-resources)))


(defn update-time-of-status-change
  [job]
  (assoc job :time-of-status-change (time/now-str)))


(defn job-cond->addition
  [{:keys [target-resource affected-resources progress status-message] :as job}]
  (cond-> job
          status-message update-time-of-status-change
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
            status-message (update-time-of-status-change)
            job-in-final-state? (assoc :progress 100)
            (and job-in-final-state?
                 started) (assoc :duration (time/time-between-date-now started :seconds)))))


(defn can-get-context?
  [resource request]
  (let [authn-info   (auth/current-authentication request)
        active-claim (auth/current-active-claim request)]
    (or (and (a/can-manage? resource request)
             (str/starts-with? active-claim "nuvlabox/"))
        (a/is-admin? authn-info))))


(defn throw-cannot-get-context
  [resource request]
  (if (can-get-context? resource request)
    resource
    (throw (ru/ex-unauthorized (:id resource)))))


(defn get-context->response
  [& resources]
  (->> resources
       (remove nil?)
       (mapcat #(if (map? %) (vector %) %))
       (map #(vector (:id %) (dissoc % :operations :acl)))
       (into {})
       r/json-response))


(defmulti get-context (fn [{:keys [target-resource action] :as resource}]
                        [(some-> target-resource :href (str/split #"/") first) action]))


(defmethod get-context :default
  [resource]
  (get-context->response))
