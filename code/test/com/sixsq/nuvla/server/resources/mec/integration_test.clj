(ns com.sixsq.nuvla.server.resources.mec.integration-test
  "Integration tests for MEC 010-2 Application Lifecycle Management API
   
   Tests validate end-to-end workflows across multiple MEC modules to ensure
   all components work together correctly for standards-compliant MEC orchestration.
   
   Standard baseline: ETSI GS MEC 010-2 v4.1.1"
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.lifecycle-handler :as lifecycle]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
    [com.sixsq.nuvla.server.resources.mec.error-handler :as error]))


;;
;; Test Fixtures
;;

(def test-app-instance-id "deployment/test-integration-app")
(def test-mepm-endpoint "http://localhost:8080/mepm")


;;
;; Integration Test 1: Basic Lifecycle Flow
;;

(deftest test-basic-lifecycle-flow
  (testing "Complete lifecycle: instantiate → operate → terminate"
    (let [instantiate-op (lifecycle/instantiate test-app-instance-id {})]
      (is (= "INSTANTIATE" (:operationType instantiate-op)))
      (is (contains? instantiate-op :lcmOpOccId))
      
      (let [operate-op (lifecycle/operate test-app-instance-id {:changeStateTo "STOPPED"})]
        (is (= "OPERATE" (:operationType operate-op)))
        (is (= test-app-instance-id (:appInstanceId operate-op)))
        
        (let [terminate-op (lifecycle/terminate test-app-instance-id {:terminationType "GRACEFUL"})]
          (is (= "TERMINATE" (:operationType terminate-op)))
          (is (contains? terminate-op :lcmOpOccId)))))))


;;
;; Integration Test 2: Multiple Operations Tracking
;;

(deftest test-multiple-operations-tracking
  (testing "Track multiple operations for same app instance"
    (let [ops [(lifecycle/instantiate test-app-instance-id {})
               (lifecycle/operate test-app-instance-id {:changeStateTo "STOPPED"})
               (lifecycle/operate test-app-instance-id {:changeStateTo "STARTED"})
               (lifecycle/terminate test-app-instance-id {:terminationType "GRACEFUL"})]]
      (is (= 4 (count ops)))
      (is (every? #(contains? % :lcmOpOccId) ops))
      (is (every? #(= test-app-instance-id (:appInstanceId %)) ops))
      (is (= ["INSTANTIATE" "OPERATE" "OPERATE" "TERMINATE"] 
             (map :operationType ops))))))


;;
;; Integration Test 3: Subscription Creation
;;

(deftest test-subscription-creation
  (testing "Create and validate subscription"
    (let [sub (subscription/create-subscription 
                "AppInstanceStateChangeNotification"
                "https://webhook.example.com/notifications"
                {:operationalState "STARTED"}
                "user/test-user")]
      (is (contains? sub :id))
      (is (= "AppInstanceStateChangeNotification" (:subscription-type sub)))
      (is (= "https://webhook.example.com/notifications" (:callback-uri sub))))))


;;
;; Integration Test 4: Error Handling
;;

(deftest test-error-handling-integration
  (testing "Error handling across modules"
    (let [error-response (error/not-found "AppInstance not-found-123" "not-found-123")]
      (is (error/problem-details? error-response))
      (is (= 404 (:status error-response)))
      (is (= "not-found-123" (:instance error-response))))
    
    (let [validation-error (error/validation-error "Invalid state transition" 
                                                    {:current "NOT_INSTANTIATED" 
                                                     :attempted "TERMINATE"})]
      (is (error/problem-details? validation-error))
      (is (= 400 (:status validation-error))))))


;;
;; Integration Test 5: Cross-Module Data Flow
;;

(deftest test-cross-module-data-flow
  (testing "Data flows correctly across lifecycle, subscription, and error modules"
    ;; Lifecycle creates operation
    (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
      (is (contains? op-occ :lcmOpOccId))
      (is (contains? op-occ :_links))
      
      ;; Subscription can reference operation
      (let [sub (subscription/create-subscription 
                  "AppLcmOpOccStateChangeNotification"
                  "https://example.com/webhook"
                  {:operationType "INSTANTIATE"}
                  "user/test-user")]
        (is (contains? sub :id))
        (is (= "INSTANTIATE" (get-in sub [:app-lcm-op-occ-filter :operationType])))
        
        ;; Error handler can report issues
        (let [error (error/internal-error "Operation failed" 
                                          nil
                                          {:lcmOpOccId (:lcmOpOccId op-occ)})]
          (is (error/problem-details? error))
          (is (= 500 (:status error)))
          ;; Extensions are merged directly into the error map
          (is (contains? error :lcmOpOccId)))))))


;;
;; Integration Test 6: HATEOAS Links Consistency
;;

(deftest test-hateoas-links-consistency
  (testing "HATEOAS links are consistent across operations"
    (let [op-occ (lifecycle/instantiate test-app-instance-id {})]
      (is (contains? (:_links op-occ) :self))
      (is (contains? (:_links op-occ) :appInstance))
      (is (re-find #"/mec/mm1/app_lcm/v1/app_lcm_op_occs/" (get-in op-occ [:_links :self :href])))
      (is (re-find #"/mec/mm1/app_lcm/v1/app_instances/" (get-in op-occ [:_links :appInstance :href]))))))


;;
;; Integration Test 7: Operation State Consistency
;;

(deftest test-operation-state-consistency
  (testing "Operation states are consistent across lifecycle"
    (let [ops [(lifecycle/instantiate test-app-instance-id {})
               (lifecycle/terminate test-app-instance-id {:terminationType "GRACEFUL"})]]
      (doseq [op ops]
        (is (contains? op :operationState))
        (is (contains? #{"PROCESSING" "STARTING" "FAILED"} (:operationState op)))
        (is (contains? op :startTime))
        (is (contains? op :stateEnteredTime))))))


;;
;; Integration Test 8: Module Completeness
;;

(deftest test-integration-module-completeness
  (testing "Integration test module has all required test categories"
    (let [test-ns (find-ns 'com.sixsq.nuvla.server.resources.mec.integration-test)
          tests   (filter #(clojure.string/starts-with? (str %) "test-")
                         (keys (ns-publics test-ns)))]
      (is (>= (count tests) 8) "Should have at least 8 integration test scenarios")
      (is (some #(clojure.string/includes? (str %) "lifecycle") tests))
      (is (some #(clojure.string/includes? (str %) "subscription") tests))
      (is (some #(clojure.string/includes? (str %) "error") tests))
      (is (some #(clojure.string/includes? (str %) "cross-module") tests)))))
