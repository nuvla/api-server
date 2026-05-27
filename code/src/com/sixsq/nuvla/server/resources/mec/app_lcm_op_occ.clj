(ns com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ
  "MEC 010-2 Application Lifecycle Operation Occurrence Tracking
   
   Tracks lifecycle operations (instantiate, terminate, operate) and their status.
   Maps to Nuvla's job resource with MEC-specific state tracking.
   
   Standard baseline: ETSI GS MEC 010-2 v4.1.1 Section 6.2.3"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.job :as job]
    [com.sixsq.nuvla.server.util.time :as time-utils]))

(def ^:private default-base-uri
  "/mec/mm1/app_lcm/v1")


;;
;; State Mapping: Nuvla Job <-> MEC Operation
;;

(def nuvla-job-to-mec-operation-state
  "Maps Nuvla job states to MEC operation states"
  {:QUEUED     :STARTING
   :RUNNING    :PROCESSING
   :SUCCESS    :COMPLETED
   :FAILED     :FAILED
   :STOPPING   :PROCESSING
   :STOPPED    :FAILED_TEMP
   :CANCELED   :ROLLED_BACK})


(def mec-to-nuvla-job-state
  "Reverse mapping for state translation"
  {:STARTING      :QUEUED
   :PROCESSING    :RUNNING
   :COMPLETED     :SUCCESS
   :FAILED        :FAILED
   :FAILED_TEMP   :STOPPED
   :ROLLED_BACK   :CANCELED})


;;
;; Schema Definitions (using predicates for flexibility)
;;

(defn valid-lcm-op-occ-id? [id]
  (and (string? id) (str/starts-with? id "job/")))

(defn valid-operation-type? [op-type]
  (contains? #{:INSTANTIATE :TERMINATE :OPERATE} (keyword op-type)))

(defn valid-operation-state? [state]
  (contains? #{:STARTING :PROCESSING :COMPLETED :FAILED :FAILED_TEMP :ROLLED_BACK} (keyword state)))

(defn- mec-time
  [timestamp]
  (let [date (or (cond
                   (string? timestamp) (time-utils/parse-date timestamp)
                   timestamp timestamp
                   :else nil)
                 (time-utils/now))]
    {:seconds     (.toEpochSecond date)
     :nanoSeconds (.getNano date)}))


;;
;; Translation Functions
;;

(defn job->app-lcm-op-occ
  "Translates a Nuvla job to MEC AppLcmOpOcc"
  ([job]
   (job->app-lcm-op-occ job {}))
  ([job {:keys [base-uri]
         :or   {base-uri default-base-uri}}]
   (let [job-id           (:id job)
         job-state        (keyword (:state job))
         operation-type   (keyword (or (:mec-operation-type job)
                                       (:operation-type job)
                                       "INSTANTIATE"))
         mec-state        (get nuvla-job-to-mec-operation-state job-state :STARTING)
         mepm-op-id       (:mec-southbound-operation-id job)
         target-resource  (:target-resource job)
         target-id        (or (:mec-app-instance-id job)
                              (if (map? target-resource)
                                (:href target-resource)
                                target-resource))
         start-time       (or (:start-time job)
                              (:created job)
                              (time-utils/now-str))
         entered-time     (or (:state-entered-time job)
                              (:updated job)
                              start-time)]
     (cond-> {:lcmOpOccId       job-id
              :id               job-id
              :operationType    (name operation-type)
              :lcmOperation     (name operation-type)
              :operationState   (name mec-state)
              :stateEnteredTime (mec-time entered-time)
              :startTime        (mec-time start-time)
              :appInstanceId    target-id}

       mepm-op-id
       (assoc :mepmOperationId mepm-op-id)

       ;; Add error information if job failed
       (#{:FAILED :STOPPED} job-state)
       (assoc :error {:type     "about:blank"
                      :title    "Operation Failed"
                      :status   500
                      :detail   (or (:status-message job) "Operation failed")
                      :instance job-id})

       ;; Add HATEOAS links
       true
       (assoc :_links {:self        {:href (str base-uri "/app_lcm_op_occs/" job-id)}
                       :appInstance {:href (str base-uri "/app_instances/" target-id)}})))))


(defn app-lcm-op-occ->job
  "Translates MEC AppLcmOpOcc to Nuvla job (partial)"
  [app-lcm-op-occ]
  (let [operation-state (keyword (:operationState app-lcm-op-occ))
        nuvla-state     (get mec-to-nuvla-job-state operation-state :QUEUED)]
    {:id                (:lcmOpOccId app-lcm-op-occ)
     :state             (name nuvla-state)
     :operation-type    (:operationType app-lcm-op-occ)
     :target-resource   (:appInstanceId app-lcm-op-occ)
     :start-time        (:startTime app-lcm-op-occ)
     :state-entered-time (:stateEnteredTime app-lcm-op-occ)}))


;;
;; Query Functions (placeholders for integration with Nuvla CRUD)
;; These will be implemented when integrated with the actual job resource
;;

(comment
  "Integration points with Nuvla job resource:
   - get-app-lcm-op-occ: Call job CRUD read
   - list-app-lcm-op-occs: Call job CRUD query with filters
   - create-app-lcm-op-occ: Call job CRUD create
   - update-operation-state: Call job CRUD update")


;;
;; Operation State Transitions (placeholders)
;;

(comment
  "State transition functions to be implemented when integrated with job resource:
   - update-operation-state: Updates job state
   - complete-operation: Marks job as SUCCESS
   - fail-operation: Marks job as FAILED")


;;
;; Validation
;;

(defn validate-app-lcm-op-occ
  "Validates AppLcmOpOcc against MEC 010-2 requirements"
  [app-lcm-op-occ]
  (when-not (:lcmOpOccId app-lcm-op-occ)
    (throw (ex-info "lcmOpOccId is required" {:app-lcm-op-occ app-lcm-op-occ})))
  (when-not (:operationType app-lcm-op-occ)
    (throw (ex-info "operationType is required" {:app-lcm-op-occ app-lcm-op-occ})))
  (when-not (:operationState app-lcm-op-occ)
    (throw (ex-info "operationState is required" {:app-lcm-op-occ app-lcm-op-occ})))
  (when-not (valid-operation-type? (:operationType app-lcm-op-occ))
    (throw (ex-info "Invalid operationType" {:type (:operationType app-lcm-op-occ)})))
  (when-not (valid-operation-state? (:operationState app-lcm-op-occ))
    (throw (ex-info "Invalid operationState" {:state (:operationState app-lcm-op-occ)})))
  app-lcm-op-occ)


;;
;; Lifecycle Hooks
;;

(defn on-operation-started
  "Hook called when an operation starts"
  [app-lcm-op-occ]
  (log/info "MEC operation started:"
            (:operationType app-lcm-op-occ)
            "for app instance"
            (:appInstanceId app-lcm-op-occ))
  app-lcm-op-occ)


(defn on-operation-completed
  "Hook called when an operation completes"
  [lcm-op-occ-id]
  (log/info "MEC operation completed:" lcm-op-occ-id))


(defn on-operation-failed
  "Hook called when an operation fails"
  [lcm-op-occ-id error-detail]
  (log/error "MEC operation failed:" lcm-op-occ-id error-detail))
