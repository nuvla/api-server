(ns com.sixsq.nuvla.server.resources.mec.app-lcm-test
  "Tests for MEC 010-2 Application Lifecycle Management API
   
   Tests cover:
   - Schema validation and state mapping
   - CRUD operations for app instances
   - Lifecycle operations (instantiate, terminate, operate)
   - Operation occurrence tracking
   - Error handling (RFC 7807)
   
   Standard: ETSI GS MEC 010-2 v4.1.1"
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.deployment :as deployment-resource]
    [com.sixsq.nuvla.server.resources.deployment.utils :as deployment-utils]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.app-lcm :as app-lcm]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
    [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]))


;;
;; Test Fixtures
;;

(def sample-deployment
  {:id      "deployment/test-123"
   :module  "module/nginx-app"
   :state   "CREATED"
   :nuvlabox "nuvlabox/edge-host-1"
   :parent  "nuvlabox/edge-host-1"
   :module/content {:name "NGINX Application"
                    :description "Example app instance"
                    :appSoftVersion "1.2.3"
                    :appDVersion "3.2.1"}
   :module/author "test-provider"})


(def sample-mepm
  {:id          "mepm/test-1"
   :endpoint    "https://mepm.example.com:8443"
   :status      "ONLINE"
   :mec-host-id "nuvlabox/edge-host-1"})


(def sample-app-instance-info
  {:appInstanceId      "deployment/test-123"
   :appDId             "module/nginx-app"
   :appName            "NGINX Application"
   :appProvider        "test-provider"
   :instantiationState "NOT_INSTANTIATED"
   :mecHostInformation {:hostId   "nuvlabox/edge-host-1"
                        :hostName "nuvlabox/edge-host-1"}
   :_links             {:self        {:href "/mec/mm1/app_lcm/v1/app_instances/deployment/test-123"}
                        :instantiate {:href "/mec/mm1/app_lcm/v1/app_instances/deployment/test-123/instantiate"}
                        :terminate   {:href "/mec/mm1/app_lcm/v1/app_instances/deployment/test-123/terminate"}
                        :operate     {:href "/mec/mm1/app_lcm/v1/app_instances/deployment/test-123/operate"}}})

(def sample-request
  {:base-uri "https://nuvla.example/api/"})


(def sample-job
  {:id                "job/instantiate-123"
   :state             "RUNNING"
   :operation-type    "INSTANTIATE"
   :mec-southbound-operation-id "op/southbound-123"
   :target-resource   "deployment/test-123"
   :start-time        "2025-10-21T10:00:00Z"
   :state-entered-time "2025-10-21T10:00:05Z"})


(def sample-mec-instantiate-job
  {:id                 "job/instantiate-123"
   :state              "QUEUED"
   :mec-operation-type "INSTANTIATE"
   :mec-app-instance-id "deployment/test-123"
   :mec-southbound-operation-id "op/southbound-123"
   :mepm-endpoint      "https://mepm.example.com:8443"
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
   :mec-southbound-operation-id "op/southbound-456"
   :mepm-endpoint      "https://mepm.example.com:8443"
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
      (is (= "module/nginx-app" (:appPkgId result)))
      (is (= "NOT_INSTANTIATED" (:instantiationState result)))
      (is (= "NGINX Application" (:appName result)))
      (is (= "1.2.3" (:appSoftVersion result)))
      (is (= "3.2.1" (:appDVersion result)))
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
      (is (contains? (:_links result) :operate))))

  (testing "HATEOAS links use request base URI when available"
    (let [result (app-instance/deployment->app-instance-info sample-deployment sample-request)]
      (is (= "https://nuvla.example/api/mec/mm1/app_lcm/v1/app_instances/deployment/test-123"
             (get-in result [:_links :self :href])))
      (is (= "https://nuvla.example/api/mec/mm1/app_lcm/v1/app_instances/deployment/test-123/instantiate"
             (get-in result [:_links :instantiate :href])))))

  (testing "appProvider falls back to deployment owner"
    (let [result (app-instance/deployment->app-instance-info
                   (-> sample-deployment
                       (dissoc :module/author)
                       (assoc :owner "user/test-owner")))]
      (is (= "user/test-owner" (:appProvider result))))))


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
      (is (= "INSTANTIATE" (:lcmOperation result)))
      (is (= "PROCESSING" (:operationState result)))
      (is (= "deployment/test-123" (:appInstanceId result)))
      (is (= "op/southbound-123" (:mepmOperationId result)))
      (is (= {:seconds 1761040800
              :nanoSeconds 0}
             (:startTime result)))))
  
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
    (let [error (app-lcm/not-found-error "deployment/missing")]
      (is (= 404 (:status error)))
      (is (= "Resource Not Found" (:title error)))
      (is (contains? error :type))
      (is (contains? error :detail))
      (is (= "deployment/missing" (:instance error)))))
  
  (testing "Validation error has correct structure"
    (let [error (app-lcm/validation-error "Invalid input")]
      (is (= 400 (:status error)))
      (is (= "Validation Error" (:title error)))
      (is (= "Invalid input" (:detail error)))))
  
  (testing "Conflict error has correct structure"
    (let [error (app-lcm/conflict-error "Resource already exists")]
      (is (= 409 (:status error)))
      (is (= "Resource Conflict" (:title error)))
      (is (= "Resource already exists" (:detail error)))))

  (testing "Service unavailable error has correct structure"
    (let [error (app-lcm/service-unavailable-error "Upstream MEPM unavailable")]
      (is (= 503 (:status error)))
      (is (= "Service Unavailable" (:title error)))
      (is (= "Upstream MEPM unavailable" (:detail error))))))


;;
;; API Endpoint Tests
;;

(deftest test-api-routes-defined
  (testing "All required routes are defined"
    (let [routes app-lcm/routes
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
    (is (= "app_lcm/v1" app-lcm/base-uri))))


(deftest test-create-app-instance-handler
  (testing "Create app instance creates deployment-backed MEC resource"
    (let [dispatch-args (atom nil)
          response (with-redefs [crud/add (fn [_]
                                            {:status 201
                                             :body {:resource-id "deployment/test-123"}})
                                 crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)
                                 dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                                  (reset! dispatch-args [app-instance change-type previous-state])
                                                                                  [])]
                     (app-lcm/create-app-instance-handler {:body sample-create-request}))]
      (is (= 201 (:status response)))
      (is (= "deployment/test-123" (get-in response [:body :appInstanceId])))
      (is (= "module/nginx-app" (get-in response [:body :appDId])))
      (is (= "/mec/mm1/app_lcm/v1/app_instances/deployment/test-123"
             (get-in response [:headers "Location"])))
      (is (= "INSTANTIATION_STATE" (second @dispatch-args)))
      (is (= "deployment/test-123" (get-in (first @dispatch-args) [:appInstanceId])))))
  
  (testing "Create app instance validates missing appDId"
    (let [response (app-lcm/create-app-instance-handler {:body {}})]
      (is (= 400 (:status response)))
      (is (= "Validation Error" (get-in response [:body :title]))))))


(deftest test-list-app-instances-handler
  (testing "List app instances returns translated deployments"
    (let [response (with-redefs [crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-deployment]}})]
                     (app-lcm/list-app-instances-handler {:params {}}))]
      (is (= 200 (:status response)))
      (is (= 1 (count (get-in response [:body :items]))))
      (is (= "deployment/test-123" (get-in response [:body :items 0 :appInstanceId]))))))


(deftest test-create-subscription-handler
  (testing "Create subscription rejects duplicate active subscription"
    (let [add-called? (atom false)
          response    (with-redefs-fn
                        {#'app-lcm/query-user-subscriptions (fn [_]
                                                                 [{:id                  "mec-subscription/existing"
                                                                   :subscription-type   "AppLcmOpOccStateChange"
                                                                   :callback-uri        "http://localhost:18082"
                                                                   :owner               "user/alice"
                                                                   :active              true
                                                                   :app-lcm-op-occ-filter {:operation-type "INSTANTIATE"}}])
                         #'crud/add                             (fn [_]
                                                                   (reset! add-called? true)
                                                                   {:status 201
                                                                    :body {:resource-id "mec-subscription/new"}})}
                        #(app-lcm/create-subscription-handler
                           {:identity {:user-id "user/alice"}
                            :body     {:subscriptionType "AppLcmOpOccStateChange"
                                       :callbackUri      "http://localhost:18082"
                                       :appLcmOpOccFilter {:operationType "INSTANTIATE"}}}))]
      (is (= 409 (:status response)))
      (is (= "Resource Conflict" (get-in response [:body :title])))
      (is (false? @add-called?))))

  (testing "Create subscription accepts same callback with different filter"
    (let [response (with-redefs-fn
                     {#'app-lcm/query-user-subscriptions (fn [_]
                                                              [{:id                  "mec-subscription/existing"
                                                                :subscription-type   "AppLcmOpOccStateChange"
                                                                :callback-uri        "http://localhost:18082"
                                                                :owner               "user/alice"
                                                                :active              true
                                                                :app-lcm-op-occ-filter {:operation-type "TERMINATE"}}])
                      #'crud/add                             (fn [_]
                                                                {:status 201
                                                                 :body {:resource-id "mec-subscription/new"}})
                      #'crud/retrieve-by-id-as-admin1        (fn [_]
                                                                {:id                  "mec-subscription/new"
                                                                 :subscription-type   "AppLcmOpOccStateChange"
                                                                 :callback-uri        "http://localhost:18082"
                                                                 :owner               "user/alice"
                                                                 :active              true
                                                                 :app-lcm-op-occ-filter {:operation-type "INSTANTIATE"}})}
                     #(app-lcm/create-subscription-handler
                        {:identity {:user-id "user/alice"}
                         :body     {:subscriptionType "AppLcmOpOccStateChange"
                                    :callbackUri      "http://localhost:18082"
                                    :appLcmOpOccFilter {:operationType "INSTANTIATE"}}}))]
      (is (= 201 (:status response)))
      (is (= "subscription/new" (get-in response [:body :id]))))))


(deftest test-get-app-instance-handler
  (testing "Get app instance returns translated deployment"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm/get-app-instance-handler {:params {:id "deployment/test-123"}}))]
      (is (= 200 (:status response)))
      (is (= "deployment/test-123" (get-in response [:body :appInstanceId])))))
  
  (testing "Get app instance returns not found"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (throw (ex-info "missing"
                                                                               {:status 404})))]
                     (app-lcm/get-app-instance-handler {:params {:id "deployment/missing"}}))]
      (is (= 404 (:status response))))))


(deftest test-delete-app-instance-handler
  (testing "Delete app instance deletes deployment in NOT_INSTANTIATED state"
    (let [deleted (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)
                                 crud/delete (fn [request]
                                               (reset! deleted request)
                                               {:status 200})]
                     (app-lcm/delete-app-instance-handler {:params {:id "deployment/test-123"}}))]
      (is (= 204 (:status response)))
      (is (= "deployment" (get-in @deleted [:params :resource-name])))))
  
  (testing "Delete app instance rejects instantiated deployment"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))]
                     (app-lcm/delete-app-instance-handler {:params {:id "deployment/test-123"}}))]
      (is (= 409 (:status response)))
      (is (= "Resource Conflict" (get-in response [:body :title]))))))


(deftest test-list-app-lcm-op-occs-handler
  (testing "List operation occurrences returns translated jobs"
    (let [response (with-redefs [crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-job]}})]
                     (app-lcm/list-app-lcm-op-occs-handler {:params {}}))]
      (is (= 200 (:status response)))
      (is (= 1 (count (get-in response [:body :items]))))
      (is (= "job/instantiate-123" (get-in response [:body :items 0 :lcmOpOccId])))))

  (testing "List operation occurrences overlays southbound state for non-final jobs"
    (let [response (with-redefs [crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-mec-operate-job]}})
                                 mm3/get-operation (fn [_ operation-id & _]
                                                     {:success? true
                                                      :status 200
                                                      :data {:id operation-id
                                                             :status "PROCESSING"}})]
                     (app-lcm/list-app-lcm-op-occs-handler {:params {}}))]
      (is (= 200 (:status response)))
      (is (= "PROCESSING" (get-in response [:body :items 0 :operationState])))
      (is (= "op/southbound-456" (get-in response [:body :items 0 :mepmOperationId]))))))


(deftest test-get-app-lcm-op-occ-handler
  (testing "Get operation occurrence returns translated job"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-job)]
                     (app-lcm/get-app-lcm-op-occ-handler {:params {:id "job/instantiate-123"}}))]
      (is (= 200 (:status response)))
      (is (= "INSTANTIATE" (get-in response [:body :operationType])))))
  
  (testing "Non-MEC job is hidden from operation occurrence endpoint"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               {:id "job/other"
                                                                :action "start_deployment"
                                                                :state "RUNNING"
                                                                :target-resource "deployment/test-123"})]
                     (app-lcm/get-app-lcm-op-occ-handler {:params {:id "job/other"}}))]
      (is (= 404 (:status response)))))

  (testing "Get operation occurrence overlays southbound operation state when available"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-mec-instantiate-job)
                                 mm3/get-operation (fn [_ _ & _]
                                                     {:success? true
                                                      :status 200
                                                      :data {:id "op/southbound-123"
                                                             :status "COMPLETED"}})]
                     (app-lcm/get-app-lcm-op-occ-handler {:params {:id "job/instantiate-123"}}))]
      (is (= 200 (:status response)))
      (is (= "COMPLETED" (get-in response [:body :operationState])))
      (is (= "op/southbound-123" (get-in response [:body :mepmOperationId])))))

  (testing "Get operation occurrence keeps local state if southbound lookup fails"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-mec-instantiate-job)
                                 mm3/get-operation (fn [_ _ & _]
                                                     {:success? false
                                                      :status 503
                                                      :error :server-error
                                                      :message "degraded"})]
                     (app-lcm/get-app-lcm-op-occ-handler {:params {:id "job/instantiate-123"}}))]
      (is (= 200 (:status response)))
      (is (= "STARTING" (get-in response [:body :operationState]))))))


(deftest test-lifecycle-handlers-use-shared-submit-path
  (let [calls (atom [])]
    (with-redefs [app-lcm/submit-lifecycle-operation! (fn [request operation-type]
                                                        (swap! calls conj [operation-type request])
                                                        {:lcmOpOccId    (str "job/" (str/lower-case operation-type))
                                                         :operationType operation-type})]
      (let [instantiate-response (app-lcm/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                            :body   {:grantId "grant-123"}})
            terminate-response   (app-lcm/terminate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                          :body   {:terminationType "GRACEFUL"}})
            operate-response     (app-lcm/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                        :body   {:changeStateTo "STARTED"}})]
        (is (= 202 (:status instantiate-response)))
        (is (= 202 (:status terminate-response)))
        (is (= 202 (:status operate-response)))
        (is (= ["INSTANTIATE" "TERMINATE" "OPERATE"]
               (map first @calls)))))))


(deftest test-instantiate-app-instance-handler
  (testing "Instantiate uses deployment start action and returns persisted MEC job"
    (let [action-request (atom nil)
          edit-request   (atom nil)
          dispatch-args  (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)
                                 crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-mepm]}})
                                 mm3/query-capabilities (fn [_ & _]
                                                          {:success? true
                                                           :status 200
                                                           :data {:platforms ["kubernetes"]}})
                                 mm3/query-resources (fn [_ & _]
                                                       {:success? true
                                                        :status 200
                                                        :data {:cpu-cores 8}})
                                 crud/do-action (fn [request]
                                                  (reset! action-request request)
                                                  {:status 202
                                                   :body {:location "job/instantiate-123"}})
                                 crud/edit-by-id-as-admin (fn [resource-id body]
                                                            (reset! edit-request {:id resource-id
                                                                                  :body body})
                                                            {:status 200})
                                 crud/retrieve-by-id-as-admin (fn [_]
                                                                sample-mec-instantiate-job)
                                 dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [op-occ change-type previous-state]
                                                                                     (reset! dispatch-args [op-occ change-type previous-state])
                                                                                     [])]
                     (app-lcm/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                   :body   {}}))]
      (is (= 202 (:status response)))
      (is (= "/mec/mm1/app_lcm/v1/app_lcm_op_occs/job/instantiate-123"
             (get-in response [:headers "Location"])))
      (is (= "start" (get-in @action-request [:params :action])))
      (is (= "INSTANTIATE" (get-in @action-request [:body :job-attrs :mec-operation-type])))
      (is (= "deployment/test-123" (get-in @action-request [:body :job-attrs :mec-app-instance-id])))
      (is (= "mepm/test-1" (get-in @action-request [:body :job-attrs :mepm-id])))
      (is (= "INSTANTIATE" (get-in @edit-request [:body :mec-operation-type])))
      (is (= "mepm/test-1" (get-in @edit-request [:body :mepm-id])))
      (is (= "INSTANTIATE" (get-in response [:body :operationType])))
      (is (= "OPERATION_STATE" (second @dispatch-args)))
      (is (= "job/instantiate-123" (get-in (first @dispatch-args) [:lcmOpOccId])))))

  (testing "Instantiate omits nil MEC job attributes"
    (let [action-request (atom nil)
          edit-request   (atom nil)
          hostless-mepm  (dissoc sample-mepm :mec-host-id)
          _response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                                sample-deployment)
                                  crud/query (fn [_]
                                               {:status 200
                                                :body {:resources [hostless-mepm]}})
                                  mm3/query-capabilities (fn [_ & _]
                                                           {:success? true
                                                            :status 200
                                                            :data {:platforms ["kubernetes"]}})
                                  mm3/query-resources (fn [_ & _]
                                                        {:success? true
                                                         :status 200
                                                         :data {:cpu-cores 8}})
                                  crud/do-action (fn [request]
                                                   (reset! action-request request)
                                                   {:status 202
                                                    :body {:location "job/instantiate-123"}})
                                  crud/edit-by-id-as-admin (fn [resource-id body]
                                                             (reset! edit-request {:id resource-id
                                                                                   :body body})
                                                             {:status 200})
                                  crud/retrieve-by-id-as-admin (fn [_]
                                                                 sample-mec-instantiate-job)
                                  dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [& _] [])]
                      (app-lcm/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                    :body   nil}))]
      (is (nil? (get-in @action-request [:body :job-attrs :mec-host-id])))
      (is (nil? (get-in @action-request [:body :job-attrs :mec-request-params])))
      (is (not (contains? (get-in @action-request [:body :job-attrs]) :mec-host-id)))
      (is (not (contains? (get-in @action-request [:body :job-attrs]) :mec-request-params)))
      (is (not (contains? (:body @edit-request) :mec-host-id)))
      (is (not (contains? (:body @edit-request) :mec-request-params)))))

  (testing "Instantiate rejects already instantiated app instance"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))]
                     (app-lcm/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                   :body   {}}))]
      (is (= 409 (:status response)))))

  (testing "Instantiate rejects unsupported request fields"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm/instantiate-app-instance-handler
                       {:params {:id "deployment/test-123"}
                        :body   {:appERRORId "1234"}}))]
      (is (= 400 (:status response)))))
  )


(deftest test-terminate-app-instance-handler
  (testing "Terminate uses deployment stop action and returns persisted MEC job"
    (let [action-request (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))
                                 crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-mepm]}})
                                 mm3/query-capabilities (fn [_ & _]
                                                          {:success? true
                                                           :status 200
                                                           :data {:platforms ["kubernetes"]}})
                                 mm3/query-resources (fn [_ & _]
                                                       {:success? true
                                                        :status 200
                                                        :data {:cpu-cores 8}})
                                 crud/do-action (fn [request]
                                                  (reset! action-request request)
                                                  {:status 202
                                                   :body {:location "job/terminate-123"}})
                                 crud/edit-by-id-as-admin (fn [_ _]
                                                            {:status 200})
                                 crud/retrieve-by-id-as-admin (fn [_]
                                                                sample-mec-terminate-job)
                                 dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [& _] [])]
                     (app-lcm/terminate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                 :body   {:terminationType "GRACEFUL"}}))]
      (is (= 202 (:status response)))
      (is (= "/mec/mm1/app_lcm/v1/app_lcm_op_occs/job/terminate-123"
             (get-in response [:headers "Location"])))
      (is (= "stop" (get-in @action-request [:params :action])))
      (is (= "TERMINATE" (get-in @action-request [:body :job-attrs :mec-operation-type])))
      (is (= "deployment/test-123" (get-in @action-request [:body :job-attrs :mec-app-instance-id])))
      (is (= "TERMINATE" (get-in response [:body :operationType])))))

  (testing "Terminate rejects unsupported request fields"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STARTED"))]
                     (app-lcm/terminate-app-instance-handler
                       {:params {:id "deployment/test-123"}
                        :body   {:terminationERRORType "FORCEFUL"}}))]
      (is (= 400 (:status response)))))

  (testing "Terminate rejects not-instantiated app instance"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm/terminate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                 :body   {:terminationType "GRACEFUL"}}))]
      (is (= 409 (:status response))))))


(deftest test-operate-app-instance-handler
  (testing "Operate STARTED uses deployment start action and returns persisted MEC job"
    (let [action-request (atom nil)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STOPPED"))
                                 crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-mepm]}})
                                 mm3/query-capabilities (fn [_ & _]
                                                          {:success? true
                                                           :status 200
                                                           :data {:platforms ["kubernetes"]}})
                                 mm3/query-resources (fn [_ & _]
                                                       {:success? true
                                                        :status 200
                                                        :data {:cpu-cores 8}})
                                 crud/do-action (fn [request]
                                                  (reset! action-request request)
                                                  {:status 202
                                                   :body {:location "job/operate-123"}})
                                 crud/edit-by-id-as-admin (fn [_ _]
                                                            {:status 200})
                                 crud/retrieve-by-id-as-admin (fn [_]
                                                                sample-mec-operate-job)
                                 dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [& _] [])]
                     (app-lcm/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                               :body   {:changeStateTo :STARTED}}))]
      (is (= 202 (:status response)))
      (is (= "start" (get-in @action-request [:params :action])))
      (is (= "STARTED" (get-in @action-request [:body :changeStateTo])))
      (is (= "OPERATE" (get-in @action-request [:body :job-attrs :mec-operation-type])))
      (is (= "deployment/test-123" (get-in @action-request [:body :job-attrs :mec-app-instance-id])))
      (is (= "OPERATE" (get-in response [:body :operationType])))))

  (testing "Operate rejects invalid requested state"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               (assoc sample-deployment :state "STOPPED"))]
                     (app-lcm/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                               :body   {:changeStateTo "PAUSED"}}))]
      (is (= 400 (:status response)))))

  (testing "Operate STARTED requires STOPPED deployment state"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)]
                     (app-lcm/operate-app-instance-handler {:params {:id "deployment/test-123"}
                                                               :body   {:changeStateTo "STARTED"}}))]
      (is (= 409 (:status response))))))


(deftest test-mec-restart-preserves-deployment-parameters
  (let [deleted?    (atom false)
        created-job (atom nil)
        request     {:params {:resource-name "deployment"
                              :uuid          "test-123"
                              :action        "start"}
                     :body   {:job-attrs {:mec-operation-type   "OPERATE"
                                          :mec-app-instance-id "deployment/test-123"}}}]
    (with-redefs [crud/retrieve-by-id-as-admin (fn [_]
                                                 (assoc sample-deployment
                                                        :state "STOPPED"
                                                        :execution-mode "push"))
                  deployment-utils/throw-when-payment-required (fn [resource _] resource)
                  deployment-utils/throw-can-not-access-registries-creds identity
                  deployment-utils/throw-can-not-access-helm-repo-cred identity
                  deployment-utils/throw-can-not-access-helm-repo-url identity
                  deployment-utils/generate-api-key-secret (fn [_ _]
                                                             {:api-key    "api-key"
                                                              :api-secret "api-secret"})
                  deployment-resource/edit-deployment (fn [resource _]
                                                       resource)
                  deployment-utils/delete-child-resources (fn [& _]
                                                            (reset! deleted? true))
                  deployment-utils/create-job (fn [_ _ action execution-mode & {:keys [job-attrs]}]
                                                (reset! created-job {:action         action
                                                                     :execution-mode execution-mode
                                                                     :job-attrs      job-attrs})
                                                {:status 202})]
      (let [response (crud/do-action request)]
        (is (= 202 (:status response)))
        (is (false? @deleted?))
        (is (= {:action         "start_deployment"
                :execution-mode "push"
                :job-attrs      {:mec-operation-type   "OPERATE"
                                 :mec-app-instance-id "deployment/test-123"}}
               @created-job))))))


(deftest test-mec-terminate-allows-stopped-deployment
  (let [created-job (atom nil)
        request     {:params {:resource-name "deployment"
                              :uuid          "test-123"
                              :action        "stop"}
                     :body   {:job-attrs {:mec-operation-type   "TERMINATE"
                                          :mec-app-instance-id "deployment/test-123"}}}]
    (with-redefs [crud/retrieve-by-id-as-admin (fn [_]
                                                 (assoc sample-deployment
                                                        :state "STOPPED"
                                                        :execution-mode "push"))
                  deployment-resource/edit-deployment (fn [resource _]
                                                       resource)
                  deployment-utils/create-job (fn [_ _ action execution-mode & {:keys [job-attrs payload]}]
                                                (reset! created-job {:action         action
                                                                     :execution-mode execution-mode
                                                                     :job-attrs      job-attrs
                                                                     :payload        payload})
                                                {:status 202})]
      (let [response (crud/do-action request)]
        (is (= 202 (:status response)))
        (is (= {:action         "stop_deployment"
                :execution-mode "push"
                :job-attrs      {:mec-operation-type   "TERMINATE"
                                 :mec-app-instance-id "deployment/test-123"}
                :payload        {}}
               @created-job))))))


(deftest test-mepm-resolution-behavior
  (testing "Instantiate fails when multiple eligible MEPMs exist without explicit host association"
    (let [hostless-deployment (dissoc sample-deployment :nuvlabox)
          response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               hostless-deployment)
                                 crud/query (fn [_]
                                              {:status 200
                                               :body {:resources [sample-mepm
                                                                  (assoc sample-mepm :id "mepm/test-2" :mec-host-id "nuvlabox/edge-host-2")]}})]
                     (app-lcm/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                   :body   {}}))]
      (is (= 409 (:status response)))))

  (testing "Instantiate fails when no eligible MEPM exists for targeted host"
    (let [response (with-redefs [crud/get-resource-throw-nok (fn [_ _]
                                                               sample-deployment)
                                 crud/query (fn [_]
                                              {:status 200
                                               :body {:resources []}})]
                     (app-lcm/instantiate-app-instance-handler {:params {:id "deployment/test-123"}
                                                                   :body   {}}))]
      (is (= 503 (:status response)))
      (is (= "Service Unavailable" (get-in response [:body :title])))
      (is (= 503 (get-in response [:body :status]))))))


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
