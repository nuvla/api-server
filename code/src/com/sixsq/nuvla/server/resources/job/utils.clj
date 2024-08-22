(ns com.sixsq.nuvla.server.resources.job.utils
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.util.general :as util-general]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as time]
    [com.sixsq.nuvla.server.util.zookeeper :as uzk]))

(def resource-type "job")

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
  (update job :status-message util-general/truncate 100000))

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

(defn create-job
  [target-resource action acl created-by & {:keys [priority affected-resources
                                                   execution-mode payload
                                                   parent-job]}]
  (let [job-map        (cond-> {:action          action
                                :target-resource {:href target-resource}
                                :acl             acl
                                :created-by      created-by}
                               priority (assoc :priority priority)
                               parent-job (assoc :parent-job parent-job)
                               affected-resources (assoc :affected-resources affected-resources)
                               execution-mode (assoc :execution-mode execution-mode)
                               payload (assoc :payload payload))
        create-request {:params      {:resource-name "job"}
                        :body        job-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))

(defn create-bulk-job
  [action-name target-resource request acl payload]
  (let [json-payload (-> payload
                         (assoc :authn-info (auth/current-authentication request))
                         (json/write-str))
        {{job-id     :resource-id
          job-status :status} :body} (create-job target-resource action-name acl
                                                 (auth/current-user-id request)
                                                 :payload json-payload)]
    (when (not= job-status 201)
      (throw (r/ex-response
               (str "unable to create async job for " action-name)
               500 target-resource)))
    (r/map-response (str "starting " action-name " with async " job-id)
                    202 target-resource job-id)))

(defn query-jobs
  [{:keys [orderby last select action target-resource states]}]
  (->> (cond-> {:filter (parser/parse-cimi-filter
                          (str/join " and "
                                    (cond-> []
                                            action (conj (str "action='" action "'"))
                                            target-resource (conj (str "target-resource/href='" target-resource "'"))
                                            (seq states) (conj (u/filter-eq-vals "state" states)))))}
               orderby (assoc :orderby orderby)
               last (assoc :last last)
               select (assoc :select select))
       (assoc {} :cimi-params)
       (crud/query-as-admin resource-type)
       second))

(defn existing-job-id-not-in-final-state
  ([target-resource]
   (existing-job-id-not-in-final-state target-resource nil))
  ([target-resource action]
   (-> {:target-resource target-resource
        :action          action
        :states          [state-queued state-running]
        :last            1
        :orderby         [["created" :desc]]
        :select          ["id"]}
       query-jobs
       first
       :id)))
