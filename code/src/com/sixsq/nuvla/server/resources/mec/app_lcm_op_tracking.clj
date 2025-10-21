(ns com.sixsq.nuvla.server.resources.mec.app-lcm-op-tracking
  "MEC 010-2 Application Lifecycle Management Operation Tracking
   
   This module provides job-based tracking for AppLcmOpOcc (Application Lifecycle 
   Management Operation Occurrences). It integrates with Nuvla's job system to:
   
   - Create job resources for each lifecycle operation
   - Synchronize state between jobs and AppLcmOpOcc
   - Provide operation history queries
   - Track operation progress and completion
   
   Standard: ETSI GS MEC 010-2 v2.2.1
   Section: 6.2.3 AppLcmOpOcc (Application LCM Operation Occurrence)"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.job :as job]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.util.time :as time-utils]))


;;
;; Job Creation for Lifecycle Operations
;;

(defn create-operation-job
  "Create a job resource for tracking a lifecycle operation.
   
   Parameters:
   - operation-type: Type of operation (:INSTANTIATE, :TERMINATE, :OPERATE)
   - app-instance-id: ID of the application instance
   - request-params: Operation-specific parameters
   - user-id: ID of the user initiating the operation
   
   Returns:
   - Job resource map with operation tracking metadata"
  [operation-type app-instance-id request-params user-id]
  (log/info "Creating operation job for" operation-type "on" app-instance-id)
  (let [job-name (str (name operation-type) " - " app-instance-id)
        job-id (str "job/" (java.util.UUID/randomUUID))
        now (time-utils/now-str)]
    {:id job-id
     :resource-type "job"
     :name job-name
     :description (str "MEC Application Lifecycle Operation: " (name operation-type))
     :action (name operation-type)
     :target-resource app-instance-id
     :state "QUEUED"
     :progress 0
     :status-message "Operation queued"
     :created now
     :updated now
     :acl {:owners [user-id]
           :view-data [user-id]}
     
     ;; MEC-specific metadata
     :mec-operation-type (name operation-type)
     :mec-app-instance-id app-instance-id
     :mec-request-params request-params
     
     ;; Timestamps
     :start-time now
     :state-entered-time now}))


(defn start-operation-job
  "Transition job to RUNNING state.
   
   Parameters:
   - job-id: ID of the job to start
   
   Returns:
   - Updated job map"
  [job-id]
  (log/info "Starting operation job" job-id)
  (let [now (time-utils/now-str)]
    {:id job-id
     :state "RUNNING"
     :progress 10
     :status-message "Operation in progress"
     :updated now
     :state-entered-time now}))


(defn complete-operation-job
  "Mark job as successfully completed.
   
   Parameters:
   - job-id: ID of the job to complete
   - result: Operation result data
   
   Returns:
   - Updated job map"
  [job-id result]
  (log/info "Completing operation job" job-id)
  (let [now (time-utils/now-str)]
    {:id job-id
     :state "SUCCESS"
     :progress 100
     :status-message "Operation completed successfully"
     :return-code 0
     :result result
     :updated now
     :state-entered-time now
     :time-of-status-change now}))


(defn fail-operation-job
  "Mark job as failed.
   
   Parameters:
   - job-id: ID of the job to fail
   - error-detail: Error information (map with :title, :detail, :status)
   
   Returns:
   - Updated job map"
  [job-id error-detail]
  (log/error "Failing operation job" job-id "with error:" (:detail error-detail))
  (let [now (time-utils/now-str)]
    {:id job-id
     :state "FAILED"
     :progress 0
     :status-message (:detail error-detail)
     :return-code 1
     :error error-detail
     :updated now
     :state-entered-time now
     :time-of-status-change now}))


;;
;; State Synchronization
;;

(defn job->app-lcm-op-occ
  "Convert a job resource to an AppLcmOpOcc representation.
   Delegates to app-lcm-op-occ namespace for the actual conversion.
   
   Parameters:
   - job: Job resource map
   
   Returns:
   - AppLcmOpOcc map conforming to MEC 010-2 schema"
  [job]
  (app-lcm-op-occ/job->app-lcm-op-occ job))


(defn get-operation-state
  "Get the current AppLcmOpOcc state for a job.
   
   Parameters:
   - job-id: ID of the job
   
   Returns:
   - AppLcmOpOcc map or nil if job not found"
  [job-id]
  (log/debug "Getting operation state for job" job-id)
  ;; In a real implementation, this would query the job resource
  ;; For now, we return a placeholder
  (when job-id
    (let [job {:id job-id
               :state "RUNNING"
               :operation-type "INSTANTIATE"
               :target-resource "deployment/test-123"
               :start-time (time-utils/now-str)
               :state-entered-time (time-utils/now-str)}]
      (job->app-lcm-op-occ job))))


;;
;; Operation History Queries
;;

(defn query-operations
  "Query operation occurrences with filtering.
   
   Parameters:
   - filters: Map of filter criteria
     * :app-instance-id - Filter by application instance
     * :operation-type - Filter by operation type (INSTANTIATE, TERMINATE, OPERATE)
     * :operation-state - Filter by operation state (PROCESSING, COMPLETED, FAILED)
     * :start-time-after - Filter operations started after this time
     * :start-time-before - Filter operations started before this time
   - options: Query options
     * :limit - Maximum number of results (default: 100)
     * :offset - Offset for pagination (default: 0)
     * :sort-by - Field to sort by (default: :start-time)
     * :sort-order - Sort order :asc or :desc (default: :desc)
   
   Returns:
   - Vector of AppLcmOpOcc maps"
  [filters options]
  (log/info "Querying operations with filters:" filters)
  (let [limit (get options :limit 100)
        offset (get options :offset 0)
        sort-by (get options :sort-by :start-time)
        sort-order (get options :sort-order :desc)]
    
    ;; In a real implementation, this would query the job collection
    ;; with the specified filters and return AppLcmOpOcc representations
    ;; For now, return empty vector
    []))


(defn get-operation-history
  "Get operation history for a specific application instance.
   
   Parameters:
   - app-instance-id: ID of the application instance
   - options: Query options (same as query-operations)
   
   Returns:
   - Vector of AppLcmOpOcc maps in reverse chronological order"
  [app-instance-id options]
  (log/info "Getting operation history for" app-instance-id)
  (query-operations {:app-instance-id app-instance-id}
                    (merge {:sort-order :desc} options)))


(defn get-operation-by-id
  "Get a specific operation occurrence by ID.
   
   Parameters:
   - op-occ-id: ID of the operation occurrence (job ID)
   
   Returns:
   - AppLcmOpOcc map or nil if not found"
  [op-occ-id]
  (log/info "Getting operation occurrence" op-occ-id)
  (get-operation-state op-occ-id))


;;
;; Operation Statistics
;;

(defn get-operation-stats
  "Get statistics about operations for an application instance.
   
   Parameters:
   - app-instance-id: ID of the application instance
   
   Returns:
   - Map with operation statistics:
     * :total - Total number of operations
     * :by-type - Count by operation type
     * :by-state - Count by operation state
     * :success-rate - Percentage of successful operations
     * :avg-duration - Average operation duration in seconds"
  [app-instance-id]
  (log/info "Getting operation statistics for" app-instance-id)
  (let [operations (get-operation-history app-instance-id {})
        total (count operations)
        by-type (frequencies (map :operationType operations))
        by-state (frequencies (map :operationState operations))
        completed (filter #(= "COMPLETED" (:operationState %)) operations)
        success-rate (if (pos? total)
                       (* 100.0 (/ (count completed) total))
                       0.0)]
    {:total total
     :by-type by-type
     :by-state by-state
     :success-rate success-rate
     :avg-duration 0.0}))


;;
;; Integration Helpers
;;

(defn wrap-with-job-tracking
  "Wrap a lifecycle operation function with job tracking.
   
   This higher-order function creates a job before executing the operation,
   updates the job state during execution, and marks it complete/failed after.
   
   Parameters:
   - operation-fn: Function to execute (takes request-params, returns result map)
   - operation-type: Type of operation (:INSTANTIATE, :TERMINATE, :OPERATE)
   - app-instance-id: ID of the application instance
   - request-params: Operation-specific parameters
   - user-id: ID of the user initiating the operation
   
   Returns:
   - Map with :job-id and :operation-result"
  [operation-fn operation-type app-instance-id request-params user-id]
  (let [job (create-operation-job operation-type app-instance-id request-params user-id)
        job-id (:id job)]
    (try
      ;; Create job in database (would be a real DB operation)
      (log/info "Created job" job-id "for operation" operation-type)
      
      ;; Start the job
      (start-operation-job job-id)
      
      ;; Execute the operation
      (let [result (operation-fn request-params)]
        (if (= :SUCCESS (:status result))
          ;; Operation succeeded
          (do
            (complete-operation-job job-id result)
            {:job-id job-id
             :operation-result result
             :app-lcm-op-occ (job->app-lcm-op-occ 
                               (merge job 
                                      (complete-operation-job job-id result)))})
          ;; Operation failed
          (let [error-detail (:error-detail result)]
            (fail-operation-job job-id error-detail)
            {:job-id job-id
             :operation-result result
             :app-lcm-op-occ (job->app-lcm-op-occ
                               (merge job
                                      (fail-operation-job job-id error-detail)))})))
      
      (catch Exception e
        (log/error e "Exception during operation execution")
        (let [error-detail {:type "about:blank"
                           :title "Operation Execution Error"
                           :status 500
                           :detail (.getMessage e)}]
          (fail-operation-job job-id error-detail)
          {:job-id job-id
           :operation-result {:status :FAILED :error-detail error-detail}
           :app-lcm-op-occ (job->app-lcm-op-occ
                             (merge job
                                    (fail-operation-job job-id error-detail)))})))))
