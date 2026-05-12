(ns com.sixsq.nuvla.server.resources.mec.lifecycle-handler-test
  "Tests for MEC 010-2 Lifecycle Operation Handler
   
   Tests cover:
   - Instantiate operation flow
   - Terminate operation flow
   - Operate operation flow (start/stop)
   - MEPM endpoint resolution
   - Error handling and validation
   
   Standard baseline: ETSI GS MEC 010-2 v4.1.1"
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.lifecycle-handler :as lifecycle]))


;;
;; Test Fixtures
;;

(def test-app-instance-id "deployment/test-app-123")
(def test-mepm-endpoint "http://localhost:8080/mepm")


;;
;; MEPM Endpoint Resolution Tests
;;

(deftest test-resolve-mepm-endpoint
  (testing "MEPM endpoint resolution returns valid URL"
    (let [endpoint (lifecycle/resolve-mepm-endpoint test-app-instance-id)]
      (is (string? endpoint))
      (is (re-find #"^http" endpoint))
      (is (= test-mepm-endpoint endpoint)))))


;;
;; Operation Context Tests
;;

(deftest test-create-operation-context
  (testing "Operation context creation"
    (let [context (lifecycle/create-operation-context
                    :INSTANTIATE
                    test-app-instance-id
                    {:grantId "grant-123"})]
      (is (= "INSTANTIATE" (:operation-type context)))
      (is (= test-app-instance-id (:app-instance-id context)))
      (is (= {:grantId "grant-123"} (:request-params context)))
      (is (= :STARTING (:status context)))
      (is (string? (:start-time context))))))


;;
;; Instantiate Operation Tests
;;

(deftest test-instantiate-operation-structure
  (testing "Instantiate creates proper operation occurrence structure"
    (let [request-params {:grantId "grant-123"}
          op-occ         (lifecycle/instantiate test-app-instance-id request-params)]
      (is (contains? op-occ :lcmOpOccId))
      (is (= "INSTANTIATE" (:operationType op-occ)))
      (is (contains? #{"PROCESSING" "FAILED"} (:operationState op-occ)))
      (is (= test-app-instance-id (:appInstanceId op-occ)))
      (is (contains? op-occ :stateEnteredTime))
      (is (contains? op-occ :startTime))
      (is (contains? op-occ :_links)))))


(deftest test-instantiate-operation-links
  (testing "Instantiate operation has proper HATEOAS links"
    (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
      (is (contains? (:_links op-occ) :self))
      (is (contains? (:_links op-occ) :appInstance))
      (is (re-find #"/mec/mm1/app_lcm/v1/app_lcm_op_occs/" (get-in op-occ [:_links :self :href])))
      (is (re-find #"/mec/mm1/app_lcm/v1/app_instances/" (get-in op-occ [:_links :appInstance :href]))))))


;;
;; Terminate Operation Tests
;;

(deftest test-terminate-operation-structure
  (testing "Terminate creates proper operation occurrence structure"
    (let [request-params {:terminationType "FORCEFUL"}
          op-occ         (lifecycle/terminate test-app-instance-id request-params)]
      (is (contains? op-occ :lcmOpOccId))
      (is (= "TERMINATE" (:operationType op-occ)))
      (is (contains? #{"PROCESSING" "FAILED"} (:operationState op-occ)))
      (is (= test-app-instance-id (:appInstanceId op-occ)))
      (is (contains? op-occ :stateEnteredTime))
      (is (contains? op-occ :startTime))
      (is (contains? op-occ :_links)))))


(deftest test-terminate-operation-links
  (testing "Terminate operation has proper HATEOAS links"
    (let [op-occ (lifecycle/terminate test-app-instance-id {})]
      (is (contains? (:_links op-occ) :self))
      (is (contains? (:_links op-occ) :appInstance)))))


;;
;; Operate Operation Tests
;;

(deftest test-operate-operation-structure
  (testing "Operate creates proper operation occurrence structure"
    (let [request-params {:changeStateTo "STARTED"}
          op-occ         (lifecycle/operate test-app-instance-id request-params)]
      (is (contains? op-occ :lcmOpOccId))
      (is (= "OPERATE" (:operationType op-occ)))
      (is (contains? #{"PROCESSING" "FAILED"} (:operationState op-occ)))
      (is (= test-app-instance-id (:appInstanceId op-occ)))
      (is (contains? op-occ :stateEnteredTime))
      (is (contains? op-occ :startTime))
      (is (contains? op-occ :_links)))))


(deftest test-operate-valid-states
  (testing "Operate accepts STARTED state"
    (let [op-occ (lifecycle/operate test-app-instance-id {:changeStateTo "STARTED"})]
      (is (contains? op-occ :lcmOpOccId))))
  
  (testing "Operate accepts STOPPED state"
    (let [op-occ (lifecycle/operate test-app-instance-id {:changeStateTo "STOPPED"})]
      (is (contains? op-occ :lcmOpOccId))))
  
  (testing "Operate accepts keyword STARTED"
    (let [op-occ (lifecycle/operate test-app-instance-id {:changeStateTo :STARTED})]
      (is (contains? op-occ :lcmOpOccId)))))


;;
;; Error Handling Tests
;;

(deftest test-operation-error-handling
  (testing "Failed operations include error information"
    ;; Note: This test assumes MEPM is unavailable, causing failure
    (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
      (when (= "FAILED" (:operationState op-occ))
        (is (contains? op-occ :error))
        (is (contains? (:error op-occ) :type))
        (is (contains? (:error op-occ) :title))
        (is (contains? (:error op-occ) :status))
        (is (contains? (:error op-occ) :detail))
        (is (= (:lcmOpOccId op-occ) (get-in op-occ [:error :instance])))))))


;;
;; Operation ID Generation Tests
;;

(deftest test-operation-id-uniqueness
  (testing "Each operation gets a unique ID"
    (let [op1 (lifecycle/instantiate test-app-instance-id {})
          op2 (lifecycle/instantiate test-app-instance-id {})
          op3 (lifecycle/terminate test-app-instance-id {})]
      (is (not= (:lcmOpOccId op1) (:lcmOpOccId op2)))
      (is (not= (:lcmOpOccId op1) (:lcmOpOccId op3)))
      (is (not= (:lcmOpOccId op2) (:lcmOpOccId op3))))))


(deftest test-operation-id-format
  (testing "Operation IDs follow job/* format"
    (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
      (is (re-find #"^job/" (:lcmOpOccId op-occ))))))


;;
;; Timestamp Tests
;;

(deftest test-operation-timestamps
  (testing "Operations have valid timestamps"
    (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
      (is (string? (:startTime op-occ)))
      (is (string? (:stateEnteredTime op-occ)))
      ;; Basic ISO8601 format check
      (is (re-find #"\d{4}-\d{2}-\d{2}T" (:startTime op-occ)))
      (is (re-find #"\d{4}-\d{2}-\d{2}T" (:stateEnteredTime op-occ))))))


;;
;; Integration Test Summary
;;

(deftest test-phase-2-week-4-completion
  (testing "Phase 2 Week 4 deliverables"
    (testing "Instantiate endpoint operational"
      (let [op-occ (lifecycle/instantiate test-app-instance-id {:grantId "test"})]
        (is (= "INSTANTIATE" (:operationType op-occ)))))
    
    (testing "Terminate endpoint operational"
      (let [op-occ (lifecycle/terminate test-app-instance-id {})]
        (is (= "TERMINATE" (:operationType op-occ)))))
    
    (testing "Operate endpoint operational"
      (let [op-occ (lifecycle/operate test-app-instance-id {:changeStateTo "STARTED"})]
        (is (= "OPERATE" (:operationType op-occ)))))
    
    (testing "Operation occurrence tracking works"
      (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
        (is (contains? op-occ :lcmOpOccId))
        (is (contains? op-occ :operationState))
        (is (contains? op-occ :appInstanceId))))
    
    (testing "MEPM endpoint resolution works"
      (let [endpoint (lifecycle/resolve-mepm-endpoint test-app-instance-id)]
        (is (string? endpoint))
        (is (re-find #"^http" endpoint))))))


;;
;; Test Runner
;;

(defn run-tests []
  (testing "MEC 010-2 Phase 2 Week 4 Tests"
    (test-resolve-mepm-endpoint)
    (test-create-operation-context)
    (test-instantiate-operation-structure)
    (test-instantiate-operation-links)
    (test-terminate-operation-structure)
    (test-terminate-operation-links)
    (test-operate-operation-structure)
    (test-operate-valid-states)
    (test-operation-error-handling)
    (test-operation-id-uniqueness)
    (test-operation-id-format)
    (test-operation-timestamps)
    (test-phase-2-week-4-completion)))
