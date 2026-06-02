(ns com.sixsq.nuvla.server.resources.mec.job-notifications
  "Dispatch MEC notifications when deployment-backed jobs reach final states."
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]))


(def ^:private nuvla-to-mec-instantiation-state
  {:CREATED  "NOT_INSTANTIATED"
   :STARTING "INSTANTIATED"
   :STARTED  "INSTANTIATED"
   :STOPPING "INSTANTIATED"
   :STOPPED  "INSTANTIATED"
   :ERROR    "NOT_INSTANTIATED"
   :PENDING  "NOT_INSTANTIATED"
   :UNKNOWN  "NOT_INSTANTIATED"})


(def ^:private nuvla-to-mec-operational-state
  {:STARTED "STARTED"
   :STOPPED "STOPPED"})


(def ^:private processing-operation-state "PROCESSING")


(defn mec-job?
  [job]
  (and (string? (:mec-operation-type job))
       (some? (or (get-in job [:target-resource :href])
                  (:target-resource job)))))


(defn- target-resource-href
  [job]
  (let [target-resource (:target-resource job)]
    (if (map? target-resource)
      (:href target-resource)
      target-resource)))


(defn- module-id
  [deployment]
  (let [module-ref (:module deployment)]
    (if (map? module-ref)
      (:href module-ref)
      module-ref)))


(defn- deployment->app-instance
  [deployment]
  (let [state (keyword (:state deployment))]
    (cond-> {:id                  (:id deployment)
             :app-name            (or (get-in deployment [:module/content :name])
                                      (get-in deployment [:module :content :name])
                                      (get-in deployment [:module :name]))
             :app-d-id            (module-id deployment)
             :instantiation-state (get nuvla-to-mec-instantiation-state state "NOT_INSTANTIATED")}
      (contains? nuvla-to-mec-operational-state state)
      (assoc :operational-state (get nuvla-to-mec-operational-state state)))))


(defn- requested-operational-state
  [job]
  (some-> (or (get-in job [:mec-request-params :changeStateTo])
              (get-in job [:mec-request-params "changeStateTo"]))
          str
          str/upper-case))


(defn- successful-app-instance-change
  [job]
  (case (:mec-operation-type job)
    "INSTANTIATE" ["INSTANTIATION_STATE" "NOT_INSTANTIATED"]
    "TERMINATE" ["INSTANTIATION_STATE" "INSTANTIATED"]
    "OPERATE" (case (requested-operational-state job)
                "STARTED" ["OPERATIONAL_STATE" "STOPPED"]
                "STOPPED" ["OPERATIONAL_STATE" "STARTED"]
                nil)
    nil))


(defn dispatch-mec-job-finalization!
  "Best-effort dispatch of MEC notifications for final job states persisted by
   job-engine and observed through api-server CRUD hooks."
  [job]
  (when (mec-job? job)
    (try
      (let [op-occ (app-lcm-op-occ/job->app-lcm-op-occ job)]
        (dispatcher/dispatch-app-lcm-op-occ-state-change! op-occ
                                                          "OPERATION_RESULT"
                                                          processing-operation-state)
        (when (= "SUCCESS" (:state job))
          (when-let [deployment (some-> job target-resource-href crud/retrieve-by-id-as-admin)]
            (when-let [[change-type previous-state] (successful-app-instance-change job)]
              (dispatcher/dispatch-app-instance-state-change! (deployment->app-instance deployment)
                                                              change-type
                                                              previous-state)))))
      (catch Exception e
        (log/error e "Failed to dispatch MEC finalization notifications"
                   (:id job)
                   (:mec-operation-type job)))))
  nil)
