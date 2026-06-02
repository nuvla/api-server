(ns com.sixsq.nuvla.server.resources.mec.app-lcm-op-tracking-test
  "Tests for MEC 010-2 Application Lifecycle Management Operation Tracking"
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-tracking :as tracking]
    [com.sixsq.nuvla.server.util.time :as time-utils]))


;;
;; Test Fixtures
;;

(def sample-app-instance-id "deployment/test-app-123")
(def sample-user-id "user/test-user")

(def sample-instantiate-params
  {:grantId "grant/123"
   :flavourId "default"})

(def sample-terminate-params
  {:terminationType "GRACEFUL"})

(def sample-operate-params
  {:changeStateTo "STARTED"})


;;
;; Job Creation Tests
;;

(deftest test-create-operation-job
  (testing "Create instantiate operation job"
    (let [job (tracking/create-operation-job 
                :INSTANTIATE 
                sample-app-instance-id 
                sample-instantiate-params 
                sample-user-id)]
      
      (is (string? (:id job)))
      (is (.startsWith (:id job) "job/"))
      (is (= "job" (:resource-type job)))
      (is (= "INSTANTIATE" (:action job)))
      (is (= sample-app-instance-id (:target-resource job)))
      (is (= "QUEUED" (:state job)))
      (is (= 0 (:progress job)))
      (is (= "INSTANTIATE" (:mec-operation-type job)))
      (is (= sample-app-instance-id (:mec-app-instance-id job)))
      (is (= sample-instantiate-params (:mec-request-params job)))
      (is (some? (:start-time job)))
      (is (some? (:state-entered-time job)))))
  
  (testing "Create terminate operation job"
    (let [job (tracking/create-operation-job
                :TERMINATE
                sample-app-instance-id
                sample-terminate-params
                sample-user-id)]
      
      (is (= "TERMINATE" (:action job)))
      (is (= "TERMINATE" (:mec-operation-type job)))
      (is (= sample-terminate-params (:mec-request-params job)))))
  
  (testing "Create operate operation job"
    (let [job (tracking/create-operation-job
                :OPERATE
                sample-app-instance-id
                sample-operate-params
                sample-user-id)]
      
      (is (= "OPERATE" (:action job)))
      (is (= "OPERATE" (:mec-operation-type job)))
      (is (= sample-operate-params (:mec-request-params job)))))
  
  (testing "Job ACL is set correctly"
    (let [job (tracking/create-operation-job
                :INSTANTIATE
                sample-app-instance-id
                sample-instantiate-params
                sample-user-id)]
      
      (is (= [sample-user-id] (get-in job [:acl :owners])))
      (is (= [sample-user-id] (get-in job [:acl :view-data]))))))


(deftest test-start-operation-job
  (testing "Start operation job transitions to RUNNING"
    (let [job-id "job/test-123"
          updated-job (tracking/start-operation-job job-id)]
      
      (is (= job-id (:id updated-job)))
      (is (= "RUNNING" (:state updated-job)))
      (is (= 10 (:progress updated-job)))
      (is (= "Operation in progress" (:status-message updated-job)))
      (is (some? (:updated updated-job)))
      (is (some? (:state-entered-time updated-job))))))


(deftest test-complete-operation-job
  (testing "Complete operation job with result"
    (let [job-id "job/test-123"
          result {:app-instance-id "deployment/test-app-123"
                  :status "INSTANTIATED"}
          completed-job (tracking/complete-operation-job job-id result)]
      
      (is (= job-id (:id completed-job)))
      (is (= "SUCCESS" (:state completed-job)))
      (is (= 100 (:progress completed-job)))
      (is (= "Operation completed successfully" (:status-message completed-job)))
      (is (= 0 (:return-code completed-job)))
      (is (= result (:result completed-job)))
      (is (some? (:updated completed-job)))
      (is (some? (:state-entered-time completed-job)))
      (is (some? (:time-of-status-change completed-job))))))


(deftest test-fail-operation-job
  (testing "Fail operation job with error detail"
    (let [job-id "job/test-123"
          error-detail {:type "about:blank"
                       :title "MEPM Connection Failed"
                       :status 503
                       :detail "Failed to connect to MEPM endpoint"}
          failed-job (tracking/fail-operation-job job-id error-detail)]
      
      (is (= job-id (:id failed-job)))
      (is (= "FAILED" (:state failed-job)))
      (is (= 0 (:progress failed-job)))
      (is (= (:detail error-detail) (:status-message failed-job)))
      (is (= 1 (:return-code failed-job)))
      (is (= error-detail (:error failed-job)))
      (is (some? (:updated failed-job)))
      (is (some? (:state-entered-time failed-job)))
      (is (some? (:time-of-status-change failed-job))))))


;;
;; State Synchronization Tests
;;

(deftest test-job-to-app-lcm-op-occ-conversion
  (testing "Convert job to AppLcmOpOcc"
    (let [job {:id "job/instantiate-123"
               :state "RUNNING"
               :operation-type "INSTANTIATE"
               :target-resource "deployment/test-123"
               :start-time "2025-10-21T10:00:00Z"
               :state-entered-time "2025-10-21T10:00:05Z"}
          op-occ (tracking/job->app-lcm-op-occ job)]
      
      (is (some? op-occ))
      (is (= "job/instantiate-123" (:lcmOpOccId op-occ)))
      (is (= "INSTANTIATE" (:operationType op-occ)))
      (is (= "PROCESSING" (:operationState op-occ)))
      (is (= "deployment/test-123" (:appInstanceId op-occ)))
      (is (= "2025-10-21T10:00:00Z" (:startTime op-occ)))))
  
  (testing "Convert completed job to AppLcmOpOcc"
    (let [job {:id "job/instantiate-123"
               :state "SUCCESS"
               :operation-type "INSTANTIATE"
               :target-resource "deployment/test-123"
               :start-time "2025-10-21T10:00:00Z"
               :state-entered-time "2025-10-21T10:00:05Z"
               :time-of-status-change "2025-10-21T10:02:00Z"}
          op-occ (tracking/job->app-lcm-op-occ job)]
      
      (is (= "COMPLETED" (:operationState op-occ)))))
  
  (testing "Convert failed job to AppLcmOpOcc with error"
    (let [job {:id "job/instantiate-123"
               :state "FAILED"
               :operation-type "INSTANTIATE"
               :target-resource "deployment/test-123"
               :start-time "2025-10-21T10:00:00Z"
               :state-entered-time "2025-10-21T10:00:05Z"
               :status-message "MEPM connection failed"
               :error {:type "about:blank"
                      :title "Connection Error"
                      :status 503
                      :detail "MEPM connection failed"}}
          op-occ (tracking/job->app-lcm-op-occ job)]
      
      (is (= "FAILED" (:operationState op-occ)))
      (is (some? (:error op-occ)))
      (is (= "MEPM connection failed" (get-in op-occ [:error :detail]))))))


(deftest test-get-operation-state
  (testing "Get operation state for existing job"
    (let [job-id "job/test-123"
          op-occ (tracking/get-operation-state job-id)]
      
      (is (some? op-occ))
      (is (= job-id (:lcmOpOccId op-occ)))
      (is (some? (:operationType op-occ)))
      (is (some? (:operationState op-occ)))))
  
  (testing "Get operation state for non-existent job"
    (let [op-occ (tracking/get-operation-state nil)]
      (is (nil? op-occ)))))


;;
;; Operation History Tests
;;

(deftest test-query-operations
  (testing "Query operations with no filters"
    (let [operations (tracking/query-operations {} {})]
      (is (vector? operations))))
  
  (testing "Query operations with app-instance-id filter"
    (let [operations (tracking/query-operations 
                       {:app-instance-id sample-app-instance-id}
                       {})]
      (is (vector? operations))))
  
  (testing "Query operations with operation-type filter"
    (let [operations (tracking/query-operations
                       {:operation-type "INSTANTIATE"}
                       {})]
      (is (vector? operations))))
  
  (testing "Query operations with pagination"
    (let [operations (tracking/query-operations
                       {}
                       {:limit 10 :offset 0})]
      (is (vector? operations))))
  
  (testing "Query operations with sorting"
    (let [operations (tracking/query-operations
                       {}
                       {:sort-by :start-time :sort-order :desc})]
      (is (vector? operations)))))


(deftest test-get-operation-history
  (testing "Get operation history for app instance"
    (let [history (tracking/get-operation-history sample-app-instance-id {})]
      
      (is (vector? history))))
  
  (testing "Get operation history with limit"
    (let [history (tracking/get-operation-history 
                    sample-app-instance-id 
                    {:limit 5})]
      
      (is (vector? history)))))


(deftest test-get-operation-by-id
  (testing "Get operation occurrence by ID"
    (let [op-occ-id "job/test-123"
          op-occ (tracking/get-operation-by-id op-occ-id)]
      
      (is (some? op-occ))
      (is (= op-occ-id (:lcmOpOccId op-occ))))))


;;
;; Operation Statistics Tests
;;

(deftest test-get-operation-stats
  (testing "Get operation statistics for app instance"
    (let [stats (tracking/get-operation-stats sample-app-instance-id)]
      
      (is (map? stats))
      (is (contains? stats :total))
      (is (contains? stats :by-type))
      (is (contains? stats :by-state))
      (is (contains? stats :success-rate))
      (is (contains? stats :avg-duration))
      (is (number? (:total stats)))
      (is (map? (:by-type stats)))
      (is (map? (:by-state stats)))
      (is (number? (:success-rate stats)))
      (is (>= (:success-rate stats) 0.0))
      (is (<= (:success-rate stats) 100.0)))))


;;
;; Integration Tests
;;

(deftest test-wrap-with-job-tracking-success
  (testing "Wrap successful operation with job tracking"
    (let [operation-fn (fn [params]
                        {:status :SUCCESS
                         :app-instance-id sample-app-instance-id
                         :result-data {:state "INSTANTIATED"}})
          result (tracking/wrap-with-job-tracking
                   operation-fn
                   :INSTANTIATE
                   sample-app-instance-id
                   sample-instantiate-params
                   sample-user-id)]
      
      (is (some? (:job-id result)))
      (is (.startsWith (:job-id result) "job/"))
      (is (= :SUCCESS (get-in result [:operation-result :status])))
      (is (some? (:app-lcm-op-occ result)))
      (is (= "COMPLETED" (get-in result [:app-lcm-op-occ :operationState]))))))


(deftest test-wrap-with-job-tracking-failure
  (testing "Wrap failed operation with job tracking"
    (let [error-detail {:type "about:blank"
                       :title "MEPM Error"
                       :status 503
                       :detail "MEPM unavailable"}
          operation-fn (fn [params]
                        {:status :FAILED
                         :error-detail error-detail})
          result (tracking/wrap-with-job-tracking
                   operation-fn
                   :INSTANTIATE
                   sample-app-instance-id
                   sample-instantiate-params
                   sample-user-id)]
      
      (is (some? (:job-id result)))
      (is (= :FAILED (get-in result [:operation-result :status])))
      (is (some? (:app-lcm-op-occ result)))
      (is (= "FAILED" (get-in result [:app-lcm-op-occ :operationState])))
      (is (some? (get-in result [:app-lcm-op-occ :error])))))


(deftest test-wrap-with-job-tracking-exception
  (testing "Wrap operation that throws exception"
    (let [operation-fn (fn [params]
                        (throw (Exception. "Unexpected error")))
          result (tracking/wrap-with-job-tracking
                   operation-fn
                   :INSTANTIATE
                   sample-app-instance-id
                   sample-instantiate-params
                   sample-user-id)]
      
      (is (some? (:job-id result)))
      (is (= :FAILED (get-in result [:operation-result :status])))
      (is (some? (:app-lcm-op-occ result)))
      (is (= "FAILED" (get-in result [:app-lcm-op-occ :operationState])))
      (is (= "Unexpected error" (get-in result [:app-lcm-op-occ :error :detail]))))))


;;
;; Test Summary
;;

(deftest test-operation-tracking-complete
  (testing "Operation tracking module is complete"
    (is (fn? tracking/create-operation-job)
        "Job creation function exists")
    (is (fn? tracking/start-operation-job)
        "Job start function exists")
    (is (fn? tracking/complete-operation-job)
        "Job completion function exists")
    (is (fn? tracking/fail-operation-job)
        "Job failure function exists")
    (is (fn? tracking/job->app-lcm-op-occ)
        "Job to AppLcmOpOcc conversion exists")
    (is (fn? tracking/query-operations)
        "Operation query function exists")
    (is (fn? tracking/get-operation-history)
        "Operation history function exists")
    (is (fn? tracking/get-operation-by-id)
        "Get operation by ID function exists")
    (is (fn? tracking/get-operation-stats)
        "Operation statistics function exists")
    (is (fn? tracking/wrap-with-job-tracking)
        "Job tracking wrapper exists")))
)
