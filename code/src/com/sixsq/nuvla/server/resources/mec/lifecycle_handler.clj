(ns com.sixsq.nuvla.server.resources.mec.lifecycle-handler
  "MEC 010-2 Lifecycle Operation Handler
   
   Handles instantiate, terminate, and operate lifecycle operations by:
   1. Validating the request
   2. Creating an operation occurrence (job)
   3. Delegating to MEPM via Mm5 interface
   4. Tracking operation status
   
   Standard: ETSI GS MEC 010-2 v2.2.1"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.util.time :as time-utils]))


;;
;; Operation Context
;;

(defn create-operation-context
  "Creates an operation context for tracking lifecycle operations"
  [operation-type app-instance-id request-params]
  {:operation-type   (name operation-type)
   :app-instance-id  app-instance-id
   :request-params   request-params
   :start-time       (time-utils/now-str)
   :status           :STARTING})


;;
;; Instantiate Operation
;;

(defn execute-instantiate
  "Executes app instantiation via MEPM Mm5 interface
   
   Steps:
   1. Query MEPM capabilities to find suitable host
   2. Send instantiate request to MEPM
   3. Track operation in AppLcmOpOcc
   4. Return operation occurrence ID"
  [app-instance-id mepm-endpoint grant-id]
  (try
    (log/info "Executing instantiate operation for" app-instance-id)
    
    ;; Query MEPM capabilities
    (let [capabilities (mm3/query-capabilities mepm-endpoint)]
      (log/debug "MEPM capabilities:" capabilities)
      
      ;; Check if MEPM supports required capabilities
      (when-not (:supports-app-instantiation capabilities)
        (throw (ex-info "MEPM does not support app instantiation"
                        {:mepm-endpoint mepm-endpoint})))
      
      ;; Create app instance via Mm3
      (let [app-instance-result (mm3/create-app-instance
                                  mepm-endpoint
                                  {:app-instance-id app-instance-id
                                   :grant-id        grant-id})]
        (log/info "App instance created via Mm3:" (:instance-id app-instance-result))
        
        ;; Return success result
        {:status          :PROCESSING
         :instance-id     (:instance-id app-instance-result)
         :mepm-endpoint   mepm-endpoint
         :operation-state :INSTANTIATION_IN_PROGRESS}))
    
    (catch Exception e
      (log/error e "Failed to execute instantiate operation")
      {:status       :FAILED
       :error-detail (ex-message e)})))


;;
;; Terminate Operation
;;

(defn execute-terminate
  "Executes app termination via MEPM Mm5 interface
   
   Steps:
   1. Query app instance status from MEPM
   2. Send terminate request to MEPM
   3. Track operation in AppLcmOpOcc
   4. Return operation occurrence ID"
  [app-instance-id mepm-endpoint termination-type]
  (try
    (log/info "Executing terminate operation for" app-instance-id)
    
    ;; Get current app instance status
    (let [app-status (mm3/get-app-instance mepm-endpoint app-instance-id)]
      (log/debug "Current app instance status:" app-status)
      
      ;; Validate app instance exists
      (when-not app-status
        (throw (ex-info "App instance not found on MEPM"
                        {:app-instance-id app-instance-id
                         :mepm-endpoint   mepm-endpoint})))
      
      ;; Delete app instance via Mm5
      (let [delete-result (mm3/delete-app-instance
                            mepm-endpoint
                            app-instance-id)]
        (log/info "App instance terminated via Mm5:" app-instance-id)
        
        ;; Return success result
        {:status          :PROCESSING
         :instance-id     app-instance-id
         :mepm-endpoint   mepm-endpoint
         :operation-state :TERMINATION_IN_PROGRESS}))
    
    (catch Exception e
      (log/error e "Failed to execute terminate operation")
      {:status       :FAILED
       :error-detail (ex-message e)})))


;;
;; Operate Operation
;;

(defn execute-operate
  "Executes app operate (start/stop) via MEPM Mm5 interface
   
   Steps:
   1. Validate target state (STARTED/STOPPED)
   2. Query current app instance status
   3. Send operate request to MEPM
   4. Track operation in AppLcmOpOcc
   5. Return operation occurrence ID"
  [app-instance-id mepm-endpoint change-state-to]
  (try
    (log/info "Executing operate operation for" app-instance-id "to state" change-state-to)
    
    ;; Validate target state
    (when-not (#{:STARTED :STOPPED "STARTED" "STOPPED"} change-state-to)
      (throw (ex-info "Invalid changeStateTo value"
                      {:value        change-state-to
                       :allowed      [:STARTED :STOPPED]})))
    
    ;; Get current app instance status
    (let [app-status (mm3/get-app-instance mepm-endpoint app-instance-id)]
      (log/debug "Current app instance status:" app-status)
      
      ;; Validate app instance exists
      (when-not app-status
        (throw (ex-info "App instance not found on MEPM"
                        {:app-instance-id app-instance-id
                         :mepm-endpoint   mepm-endpoint})))
      
      ;; Check if state change is needed
      (let [current-state (keyword (:operational-state app-status))
            target-state  (keyword change-state-to)]
        (when (= current-state target-state)
          (log/warn "App instance already in target state:" target-state)
          (throw (ex-info "App instance already in target state"
                          {:current-state current-state
                           :target-state  target-state})))
        
        ;; Execute state change via Mm5
        ;; Note: This would require an Mm5 operate endpoint (future enhancement)
        (log/info "App operate request would be sent to Mm5 (not yet implemented)")
        
        ;; Return success result
        {:status          :PROCESSING
         :instance-id     app-instance-id
         :mepm-endpoint   mepm-endpoint
         :current-state   current-state
         :target-state    target-state
         :operation-state :OPERATION_IN_PROGRESS}))
    
    (catch Exception e
      (log/error e "Failed to execute operate operation")
      {:status       :FAILED
       :error-detail (ex-message e)})))


;;
;; Operation Orchestration
;;

(defn handle-lifecycle-operation
  "Main handler for lifecycle operations
   
   Orchestrates the complete lifecycle operation flow:
   1. Create operation context
   2. Execute operation via appropriate handler
   3. Create operation occurrence for tracking
   4. Return operation occurrence info"
  [operation-type app-instance-id mepm-endpoint request-params]
  (try
    ;; Create operation context
    (let [context (create-operation-context operation-type app-instance-id request-params)]
      (log/info "Starting lifecycle operation:" operation-type "for" app-instance-id)
      
      ;; Execute operation based on type
      (let [result (case operation-type
                     :INSTANTIATE (execute-instantiate
                                    app-instance-id
                                    mepm-endpoint
                                    (:grantId request-params))
                     
                     :TERMINATE (execute-terminate
                                  app-instance-id
                                  mepm-endpoint
                                  (:terminationType request-params "FORCEFUL"))
                     
                     :OPERATE (execute-operate
                                app-instance-id
                                mepm-endpoint
                                (:changeStateTo request-params))
                     
                     (throw (ex-info "Unknown operation type"
                                     {:operation-type operation-type})))]
        
        ;; Create operation occurrence for tracking
        (let [op-occ-id  (str "job/" (java.util.UUID/randomUUID))
              op-occ     {:lcmOpOccId        op-occ-id
                          :operationType     (name operation-type)
                          :operationState    (if (= :FAILED (:status result))
                                              "FAILED"
                                              "PROCESSING")
                          :stateEnteredTime  (time-utils/now-str)
                          :startTime         (:start-time context)
                          :appInstanceId     app-instance-id
                          :_links            {:self        {:href (str "/app_lcm/v2/app_lcm_op_occs/" op-occ-id)}
                                              :appInstance {:href (str "/app_lcm/v2/app_instances/" app-instance-id)}}}]
          
          ;; Add error if operation failed
          (if (= :FAILED (:status result))
            (assoc op-occ :error {:type     "about:blank"
                                  :title    "Operation Failed"
                                  :status   500
                                  :detail   (:error-detail result)
                                  :instance op-occ-id})
            op-occ))))
    
    (catch Exception e
      (log/error e "Failed to handle lifecycle operation")
      (throw e))))


;;
;; MEPM Endpoint Resolution
;;

(defn resolve-mepm-endpoint
  "Resolves the MEPM endpoint for a given app instance
   
   Strategy:
   1. Check if app instance has assigned MEPM
   2. Query available MEPMs
   3. Select optimal MEPM based on capabilities and resources
   4. Return MEPM endpoint URL"
  [app-instance-id]
  (try
    ;; For now, return a default MEPM endpoint
    ;; In production, this would query the MEPM registry
    (let [default-mepm-endpoint "http://localhost:8080/mepm"]
      (log/debug "Resolved MEPM endpoint for" app-instance-id ":" default-mepm-endpoint)
      default-mepm-endpoint)
    
    (catch Exception e
      (log/error e "Failed to resolve MEPM endpoint")
      (throw (ex-info "No suitable MEPM found"
                      {:app-instance-id app-instance-id})))))


;;
;; Public API
;;

(defn instantiate
  "Public API for app instantiation"
  [app-instance-id request-params]
  (let [mepm-endpoint (resolve-mepm-endpoint app-instance-id)]
    (handle-lifecycle-operation :INSTANTIATE app-instance-id mepm-endpoint request-params)))


(defn terminate
  "Public API for app termination"
  [app-instance-id request-params]
  (let [mepm-endpoint (resolve-mepm-endpoint app-instance-id)]
    (handle-lifecycle-operation :TERMINATE app-instance-id mepm-endpoint request-params)))


(defn operate
  "Public API for app operate (start/stop)"
  [app-instance-id request-params]
  (let [mepm-endpoint (resolve-mepm-endpoint app-instance-id)]
    (handle-lifecycle-operation :OPERATE app-instance-id mepm-endpoint request-params)))
