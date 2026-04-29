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
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
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


(def sample-mec-instantiate-job
  {:id                 "job/instantiate-123"
   :state              "QUEUED"
   :mec-operation-type "INSTANTIATE"
   :mec-app-instance-id "deployment/test-123"
   :target-resource    {:href "deployment/test-123"}})


(def sample-mec-terminate-job
  {:id                 "job/terminate-123"
   :state              "QUEUED"
   :mec-operation-type "TERMINATE"
   :mec-app-instance-id "deployment/test-123"
   :target-resource    {:href "deployment/test-123"}})


(def sample-mec-operate-job
  {:id                 "job/operate-123"
   :state              "QUEUED"
   :mec-operation-type "OPERATE"
   :mec-app-instance-id "deployment/test-123"
   :target-resource    {:href "deployment/test-123"}})


(def sample-create-request
  {:appDId          "module/nginx-app"
   :appName         "NGINX Application"
   :appDescription  "NGINX application instance"})


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


(deftest test-create-app-instance-handler
  (testing "Create app instance creates deployment-backed MEC resource"
    (let [response (with-redefs [crud/add (fn [_]
                                            {:status 201
                                             :body {:resource-id "deployment/test-123"}})
                                 crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm-v2/create-app-instance-handler {:body sample-create-request}))]
      (is (= 201 (:status response)))
      (is (= "deployment/test-123" (get-in response [:body :appInstanceId])))
      (is (= "module/nginx-app" (get-in response [:body :appDId])))))
  
  (testing "Create app instance validates missing appDId"
    (let [response (app-lcm-v2/create-app-instance-handler {:body {}})]
      (is (= 400 (:status response)))
      (is (= "Validation Error" (get-in response [:body :title]))))))


(deftest test-list-app-instances-handler
  (testing "List app instances returns translated deployments"
    (let [response (with-redefs [crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-deployment]}})]
                     (app-lcm-v2/list-app-instances-handler {:params {}}))]
      (is (= 200 (:status response)))
      (is (= 1 (count (get-in response [:body :items]))))
      (is (= "deployment/test-123" (get-in response [:body :items 0 :appInstanceId]))))))


(deftest test-get-app-instance-handler
  (testing "Get app instance returns translated deployment"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm-v2/get-app-instance-handler {:params {:id "deployment/test-123"}}))]
      (is (= 200 (:status response)))
      (is (= "deployment/test-123" (get-in response [:body :appInstanceId])))))
  
  (testing "Get app instance returns not found"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (throw (ex-info "missing"
                                                                               {:status 404})))]
                     (app-lcm-v2/get-app-instance-handler {:params {:id "deployment/missing"}}))]
      (is (= 404 (:status response))))))


(deftest test-delete-app-instance-handler
  (testing "Delete app instance deletes deployment in NOT_INSTANTIATED state"
    (let [deleted (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)
                                 crud/delete (fn [request]
                                               (reset! deleted request)
                                               {:status 200})]
                     (app-lcm-v2/delete-app-instance-handler {:params {:id "deployment/test-123"}}))]
      (is (= 204 (:status response)))
      (is (= "deployment" (get-in @deleted [:params :resource-name])))))
  
  (testing "Delete app instance rejects instantiated deployment"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))]
                     (app-lcm-v2/delete-app-instance-handler {:params {:id "deployment/test-123"}}))]
      (is (= 409 (:status response)))
      (is (= "Resource Conflict" (get-in response [:body :title]))))))


(deftest test-list-app-lcm-op-occs-handler
  (testing "List operation occurrences returns translated jobs"
    (let [response (with-redefs [crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-job]}})]
                     (app-lcm-v2/list-app-lcm-op-occs-handler {:params {}}))]
      (is (= 200 (:status response)))
      (is (= 1 (count (get-in response [:body :items]))))
      (is (= "job/instantiate-123" (get-in response [:body :items 0 :lcmOpOccId]))))))


(deftest test-get-app-lcm-op-occ-handler
  (testing "Get operation occurrence returns translated job"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-job)]
                     (app-lcm-v2/get-app-lcm-op-occ-handler {:params {:id "job/instantiate-123"}}))]
      (is (= 200 (:status response)))
      (is (= "INSTANTIATE" (get-in response [:body :operationType])))))
  
  (testing "Non-MEC job is hidden from operation occurrence endpoint"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               {:id "job/other"
                                                                :action "start_deployment"
                                                                :state "RUNNING"
                                                                :target-resource "deployment/test-123"})]
                     (app-lcm-v2/get-app-lcm-op-occ-handler {:params {:id "job/other"}}))]
      (is (= 404 (:status response))))))


(deftest test-instantiate-app-instance-handler
  (testing "Instantiate uses deployment start action and returns persisted MEC job"
    (let [action-request (atom nil)
          edit-request   (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)
                                 crud/do-action (fn [request]
                                                  (reset! action-request request)
                                                  {:status 202
                                                   :body {:location "job/instantiate-123"}})
                                 crud/edit-by-id-as-admin (fn [resource-id body]
                                                            (reset! edit-request {:id resource-id
                                                                                  :body body})
                                                            {:status 200})
                                 crud/retrieve-by-id-as-admin (fn [_]
                                                                sample-mec-instantiate-job)]
                     (app-lcm-v2/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                   :body   {}}))]
      (is (= 202 (:status response)))
      (is (= "start" (get-in @action-request [:params :action])))
      (is (= "INSTANTIATE" (get-in @edit-request [:body :mec-operation-type])))
      (is (= "INSTANTIATE" (get-in response [:body :operationType])))))

  (testing "Instantiate rejects already instantiated app instance"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))]
                     (app-lcm-v2/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                   :body   {}}))]
      (is (= 409 (:status response))))))


(deftest test-terminate-app-instance-handler
  (testing "Terminate uses deployment stop action and returns persisted MEC job"
    (let [action-request (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))
                                 crud/do-action (fn [request]
                                                  (reset! action-request request)
                                                  {:status 202
                                                   :body {:location "job/terminate-123"}})
                                 crud/edit-by-id-as-admin (fn [_ _]
                                                            {:status 200})
                                 crud/retrieve-by-id-as-admin (fn [_]
                                                                sample-mec-terminate-job)]
                     (app-lcm-v2/terminate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                 :body   {:terminationType "GRACEFUL"}}))]
      (is (= 202 (:status response)))
      (is (= "stop" (get-in @action-request [:params :action])))
      (is (= "TERMINATE" (get-in response [:body :operationType])))))

  (testing "Terminate rejects not-instantiated app instance"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm-v2/terminate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                 :body   {:terminationType "GRACEFUL"}}))]
      (is (= 409 (:status response))))))


(deftest test-operate-app-instance-handler
  (testing "Operate STARTED uses deployment start action and returns persisted MEC job"
    (let [action-request (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STOPPED"))
                                 crud/do-action (fn [request]
                                                  (reset! action-request request)
                                                  {:status 202
                                                   :body {:location "job/operate-123"}})
                                 crud/edit-by-id-as-admin (fn [_ _]
                                                            {:status 200})
                                 crud/retrieve-by-id-as-admin (fn [_]
                                                                sample-mec-operate-job)]
                     (app-lcm-v2/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                               :body   {:changeStateTo :STARTED}}))]
      (is (= 202 (:status response)))
      (is (= "start" (get-in @action-request [:params :action])))
      (is (= "STARTED" (get-in @action-request [:body :changeStateTo])))
      (is (= "OPERATE" (get-in response [:body :operationType])))))

  (testing "Operate rejects invalid requested state"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STOPPED"))]
                     (app-lcm-v2/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                               :body   {:changeStateTo "PAUSED"}}))]
      (is (= 400 (:status response)))))

  (testing "Operate STARTED requires STOPPED deployment state"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm-v2/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                               :body   {:changeStateTo "STARTED"}}))]
      (is (= 409 (:status response))))))


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
