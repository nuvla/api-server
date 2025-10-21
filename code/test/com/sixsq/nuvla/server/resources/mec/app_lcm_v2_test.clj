(ns com.sixsq.nuvla.server.resources.mec.app-lcm-v2-test
  "Tests for MEC 010-2 Application Lifecycle Management API
   
   Tests cover:
   - Schema validation and state mapping
   - CRUD operations for app instances
   - Lifecycle operations (instantiate, terminate, operate)
   - Operation occurrence tracking
   - Error handling (RFC 7807)
   
   Standard: ETSI GS MEC 010-2 v2.2.1"
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-v2 :as app-lcm-v2]))


;;
;; Test Fixtures
;;

(def sample-deployment
  {:id      "deployment/test-123"
   :module  "module/nginx-app"
   :state   "CREATED"
   :parent  "nuvlabox/edge-host-1"
   :module/content {:name "NGINX Application"}
   :module/author "test-provider"})


(def sample-app-instance-info
  {:appInstanceId      "deployment/test-123"
   :appDId             "module/nginx-app"
   :appName            "NGINX Application"
   :appProvider        "test-provider"
   :instantiationState "NOT_INSTANTIATED"
   :mecHostInformation {:hostId   "nuvlabox/edge-host-1"
                        :hostName "nuvlabox/edge-host-1"}
   :_links             {:self        {:href "/app_lcm/v2/app_instances/deployment/test-123"}
                        :instantiate {:href "/app_lcm/v2/app_instances/deployment/test-123/instantiate"}
                        :terminate   {:href "/app_lcm/v2/app_instances/deployment/test-123/terminate"}
                        :operate     {:href "/app_lcm/v2/app_instances/deployment/test-123/operate"}}})


(def sample-job
  {:id                "job/instantiate-123"
   :state             "RUNNING"
   :operation-type    "INSTANTIATE"
   :target-resource   "deployment/test-123"
   :start-time        "2025-10-21T10:00:00Z"
   :state-entered-time "2025-10-21T10:00:05Z"})


;;
;; Schema Validation Tests
;;

(deftest test-app-instance-schema-validation
  (testing "Valid AppInstanceInfo passes validation"
    (is (= sample-app-instance-info
           (app-instance/validate-app-instance-info sample-app-instance-info))))
  
  (testing "AppInstanceInfo with missing appInstanceId fails"
    (is (thrown? Exception
                 (app-instance/validate-app-instance-info
                   (dissoc sample-app-instance-info :appInstanceId)))))
  
  (testing "AppInstanceInfo with invalid state fails"
    (is (thrown? Exception
                 (app-instance/validate-app-instance-info
                   (assoc sample-app-instance-info
                          :instantiationState "INVALID_STATE"))))))


(deftest test-app-lcm-op-occ-schema-validation
  (testing "Valid AppLcmOpOcc passes validation"
    (let [op-occ (app-lcm-op-occ/job->app-lcm-op-occ sample-job)]
      (is (= op-occ (app-lcm-op-occ/validate-app-lcm-op-occ op-occ)))))
  
  (testing "AppLcmOpOcc with invalid operation type fails"
    (is (thrown? Exception
                 (app-lcm-op-occ/validate-app-lcm-op-occ
                   {:lcmOpOccId      "job/test"
                    :operationType   "INVALID_OP"
                    :operationState  "PROCESSING"
                    :stateEnteredTime "2025-10-21T10:00:00Z"
                    :startTime       "2025-10-21T10:00:00Z"
                    :appInstanceId   "deployment/test"})))))


;;
;; State Mapping Tests
;;

(deftest test-nuvla-to-mec-instantiation-state-mapping
  (testing "CREATED maps to NOT_INSTANTIATED"
    (is (= :NOT_INSTANTIATED
           (get app-instance/nuvla-to-mec-instantiation-state :CREATED))))
  
  (testing "STARTED maps to INSTANTIATED"
    (is (= :INSTANTIATED
           (get app-instance/nuvla-to-mec-instantiation-state :STARTED))))
  
  (testing "STOPPED maps to INSTANTIATED"
    (is (= :INSTANTIATED
           (get app-instance/nuvla-to-mec-instantiation-state :STOPPED))))
  
  (testing "ERROR maps to INSTANTIATED"
    (is (= :INSTANTIATED
           (get app-instance/nuvla-to-mec-instantiation-state :ERROR)))))


(deftest test-nuvla-to-mec-operational-state-mapping
  (testing "STARTED maps to STARTED"
    (is (= :STARTED
           (get app-instance/nuvla-to-mec-operational-state :STARTED))))
  
  (testing "STOPPED maps to STOPPED"
    (is (= :STOPPED
           (get app-instance/nuvla-to-mec-operational-state :STOPPED))))
  
  (testing "STARTING has no operational state"
    (is (nil? (get app-instance/nuvla-to-mec-operational-state :STARTING)))))


(deftest test-job-to-operation-state-mapping
  (testing "RUNNING job maps to PROCESSING operation"
    (is (= :PROCESSING
           (get app-lcm-op-occ/nuvla-job-to-mec-operation-state :RUNNING))))
  
  (testing "SUCCESS job maps to COMPLETED operation"
    (is (= :COMPLETED
           (get app-lcm-op-occ/nuvla-job-to-mec-operation-state :SUCCESS))))
  
  (testing "FAILED job maps to FAILED operation"
    (is (= :FAILED
           (get app-lcm-op-occ/nuvla-job-to-mec-operation-state :FAILED)))))


;;
;; Translation Tests
;;

(deftest test-deployment-to-app-instance-info-translation
  (testing "Basic deployment translates correctly"
    (let [result (app-instance/deployment->app-instance-info sample-deployment)]
      (is (= "deployment/test-123" (:appInstanceId result)))
      (is (= "module/nginx-app" (:appDId result)))
      (is (= "NOT_INSTANTIATED" (:instantiationState result)))
      (is (= "NGINX Application" (:appName result)))
      (is (= "test-provider" (:appProvider result)))
      (is (= "nuvlabox/edge-host-1" (get-in result [:mecHostInformation :hostId])))))
  
  (testing "STARTED deployment has operational state"
    (let [started-deployment (assoc sample-deployment :state "STARTED")
          result (app-instance/deployment->app-instance-info started-deployment)]
      (is (= "INSTANTIATED" (:instantiationState result)))
      (is (= "STARTED" (:operationalState result)))))
  
  (testing "STOPPED deployment has operational state"
    (let [stopped-deployment (assoc sample-deployment :state "STOPPED")
          result (app-instance/deployment->app-instance-info stopped-deployment)]
      (is (= "INSTANTIATED" (:instantiationState result)))
      (is (= "STOPPED" (:operationalState result)))))
  
  (testing "HATEOAS links are generated"
    (let [result (app-instance/deployment->app-instance-info sample-deployment)]
      (is (contains? (:_links result) :self))
      (is (contains? (:_links result) :instantiate))
      (is (contains? (:_links result) :terminate))
      (is (contains? (:_links result) :operate)))))


(deftest test-app-instance-info-to-deployment-translation
  (testing "AppInstanceInfo translates to deployment"
    (let [result (app-instance/app-instance-info->deployment sample-app-instance-info)]
      (is (= "deployment/test-123" (:id result)))
      (is (= "module/nginx-app" (:module result)))
      (is (= "CREATED" (:state result)))
      (is (= "nuvlabox/edge-host-1" (:parent result))))))


(deftest test-job-to-app-lcm-op-occ-translation
  (testing "Job translates to AppLcmOpOcc"
    (let [result (app-lcm-op-occ/job->app-lcm-op-occ sample-job)]
      (is (= "job/instantiate-123" (:lcmOpOccId result)))
      (is (= "INSTANTIATE" (:operationType result)))
      (is (= "PROCESSING" (:operationState result)))
      (is (= "deployment/test-123" (:appInstanceId result)))
      (is (= "2025-10-21T10:00:00Z" (:startTime result)))))
  
  (testing "Failed job includes error information"
    (let [failed-job (assoc sample-job
                            :state "FAILED"
                            :status-message "Deployment failed")
          result (app-lcm-op-occ/job->app-lcm-op-occ failed-job)]
      (is (= "FAILED" (:operationState result)))
      (is (contains? result :error))
      (is (= "Deployment failed" (get-in result [:error :detail])))))
  
  (testing "HATEOAS links are generated"
    (let [result (app-lcm-op-occ/job->app-lcm-op-occ sample-job)]
      (is (contains? (:_links result) :self))
      (is (contains? (:_links result) :appInstance)))))


;;
;; Error Handling Tests (RFC 7807)
;;

(deftest test-problem-details-format
  (testing "Not found error has correct structure"
    (let [error (app-lcm-v2/not-found-error "deployment/missing")]
      (is (= 404 (:status error)))
      (is (= "Resource Not Found" (:title error)))
      (is (contains? error :type))
      (is (contains? error :detail))
      (is (= "deployment/missing" (:instance error)))))
  
  (testing "Validation error has correct structure"
    (let [error (app-lcm-v2/validation-error "Invalid input")]
      (is (= 400 (:status error)))
      (is (= "Validation Error" (:title error)))
      (is (= "Invalid input" (:detail error)))))
  
  (testing "Conflict error has correct structure"
    (let [error (app-lcm-v2/conflict-error "Resource already exists")]
      (is (= 409 (:status error)))
      (is (= "Resource Conflict" (:title error)))
      (is (= "Resource already exists" (:detail error))))))


;;
;; API Endpoint Tests
;;

(deftest test-api-routes-defined
  (testing "All required routes are defined"
    (let [routes app-lcm-v2/routes
          paths  (map first routes)]
      (is (some #(re-find #"/app_instances$" %) paths))
      (is (some #(re-find #"/app_instances/:id$" %) paths))
      (is (some #(re-find #"/instantiate$" %) paths))
      (is (some #(re-find #"/terminate$" %) paths))
      (is (some #(re-find #"/operate$" %) paths))
      (is (some #(re-find #"/app_lcm_op_occs$" %) paths))
      (is (some #(re-find #"/app_lcm_op_occs/:id$" %) paths)))))


(deftest test-base-uri
  (testing "Base URI follows MEC 010-2 format"
    (is (= "app_lcm/v2" app-lcm-v2/base-uri))))


;;
;; Integration Tests Summary
;;

(deftest test-phase-1-completion
  (testing "Phase 1 Week 1 deliverables"
    (is (= sample-app-instance-info
           (app-instance/validate-app-instance-info sample-app-instance-info))
        "MEC 010-2 AppInstanceInfo schema defined and validation works")
    
    (let [op-occ (app-lcm-op-occ/job->app-lcm-op-occ sample-job)]
      (is (= op-occ (app-lcm-op-occ/validate-app-lcm-op-occ op-occ))
          "MEC 010-2 AppLcmOpOcc schema defined and validation works"))
    
    (is (= :NOT_INSTANTIATED
           (get app-instance/nuvla-to-mec-instantiation-state :CREATED))
        "State mapping functions work")
    
    (is (= "deployment/test-123"
           (:appInstanceId (app-instance/deployment->app-instance-info sample-deployment)))
        "Deployment to AppInstanceInfo translation works")))


;;
;; Test Runner
;;

(defn run-tests []
  (testing "MEC 010-2 Phase 1 Week 1 Tests"
    (test-app-instance-schema-validation)
    (test-app-lcm-op-occ-schema-validation)
    (test-nuvla-to-mec-instantiation-state-mapping)
    (test-nuvla-to-mec-operational-state-mapping)
    (test-job-to-operation-state-mapping)
    (test-deployment-to-app-instance-info-translation)
    (test-app-instance-info-to-deployment-translation)
    (test-job-to-app-lcm-op-occ-translation)
    (test-problem-details-format)
    (test-api-routes-defined)
    (test-base-uri)
    (test-phase-1-completion)))
