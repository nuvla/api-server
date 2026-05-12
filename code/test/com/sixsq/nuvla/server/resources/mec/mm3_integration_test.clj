(ns com.sixsq.nuvla.server.resources.mec.mm3-integration-test
  "Integration tests for Mm3 interface using mock MEPM server."
  (:require
    [clj-http.client :as http]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
    [com.sixsq.nuvla.server.resources.mec.mock-mepm-server :as mock-mepm]
    [jsonista.core :as json]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json :refer [wrap-json-body]]))

(def test-port 18080)
(def test-endpoint (str "http://localhost:" test-port))

;;
;; Test Fixtures
;;

(defn start-mock-mepm-fixture [f]
  (mock-mepm/start-server! test-port)
  (try
    (f)
    (finally
      (mock-mepm/stop-server!))))

(use-fixtures :once start-mock-mepm-fixture)

(defn reset-mepm-state-fixture [f]
  (mock-mepm/reset-state!)
  (f))

(use-fixtures :each reset-mepm-state-fixture)

;;
;; Mm3 Protocol Validation Tests
;;

(deftest test-mm3-health-check-protocol
  (testing "Health check follows ETSI MEC 003 protocol"
    (let [response (mm3/check-health test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :status))
      (is (contains? (:data response) :timestamp))
      (is (contains? (:data response) :version))
      (is (contains? (:data response) :checks)))))

(deftest test-mm3-capabilities-protocol
  (testing "Capabilities query follows ETSI MEC 003 protocol"
    (let [response (mm3/query-capabilities test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :platforms))
      (is (contains? (:data response) :services))
      (is (contains? (:data response) :api-version))
      (is (vector? (:platforms (:data response))))
      (is (vector? (:services (:data response)))))))

(deftest test-mm3-resources-protocol
  (testing "Resources query follows ETSI MEC 003 protocol"
    (let [response (mm3/query-resources test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :cpu-cores))
      (is (contains? (:data response) :memory-gb))
      (is (contains? (:data response) :storage-gb))
      (is (pos? (:cpu-cores (:data response))))
      (is (pos? (:memory-gb (:data response)))))))

(deftest test-mm3-platform-info-protocol
  (testing "Platform info follows ETSI MEC 003 protocol"
    (let [response (mm3/get-platform-info test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :platform-id))
      (is (contains? (:data response) :platform-name))
      (is (contains? (:data response) :location)))))

(deftest test-mm3-configure-platform-protocol
  (testing "Platform configuration follows ETSI MEC 003 protocol"
    (let [config {:dns-rules [{:domain "example.com" :ip "10.0.0.1"}]
                  :traffic-rules [{:priority 1 :action "allow"}]}
          response (mm3/configure-platform test-endpoint config)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :success))
      (is (:success (:data response))))))

;;
;; Error Handling Tests
;;

(deftest test-mm3-timeout-handling
  (testing "Graceful handling of MEPM timeout"
    (mock-mepm/set-error-mode! :timeout)
    (let [response (mm3/check-health test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 504 (:status response))))))

(deftest test-mm3-server-error-handling
  (testing "Graceful handling of MEPM server error"
    (mock-mepm/set-error-mode! :server-error)
    (let [response (mm3/query-capabilities test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 500 (:status response))))))

(deftest test-mm3-not-found-handling
  (testing "Graceful handling of MEPM not found"
    (mock-mepm/set-error-mode! :not-found)
    (let [response (mm3/query-resources test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 404 (:status response))))))

(deftest test-mm3-degraded-service
  (testing "Graceful handling of degraded MEPM"
    (mock-mepm/set-error-mode! :degraded)
    (let [response (mm3/check-health test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 503 (:status response))))))

;;
;; Retry Mechanism Tests
;;

(deftest test-mm3-retry-mechanism-simulation
  (testing "Mock server can simulate transient failures"
    ;; This test verifies the mock server error mode works correctly
    ;; Actual retry testing is done implicitly in other tests
    (mock-mepm/set-error-mode! :server-error)
    (let [response1 (mm3/check-health test-endpoint {:retry-attempts 1})]
      (is (not (:success? response1))))
    
    ;; Reset error mode and verify recovery
    (mock-mepm/set-error-mode! nil)
    (let [response2 (mm3/check-health test-endpoint)]
      (is (:success? response2)))))

(deftest test-mm3-connection-refused
  (testing "Graceful handling of connection refused"
    (let [bad-endpoint "http://localhost:9999"]
      (let [response (mm3/check-health bad-endpoint {:retry-attempts 1
                                                      :connect-timeout 1000})]
        (is (not (:success? response)))
        (is (= :connection-error (:error response)))))))

;;
;; End-to-End Flow Tests
;;

(deftest test-mm3-full-health-check-flow
  (testing "Complete health check flow"
    ;; Check health
    (let [health-response (mm3/check-health test-endpoint)]
      (is (:success? health-response))
      (is (= "online" (:status (:data health-response)))))

    ;; Use convenience function
    (is (mm3/healthy? test-endpoint))))

(deftest test-mm3-full-capability-query-flow
  (testing "Complete capability query flow"
    ;; Query capabilities
    (let [cap-response (mm3/query-capabilities test-endpoint)]
      (is (:success? cap-response))
      (is (seq (:platforms (:data cap-response))))
      (is (seq (:services (:data cap-response)))))

    ;; Use convenience function
    (let [capabilities (mm3/get-capabilities test-endpoint)]
      (is (some? capabilities))
      (is (contains? capabilities :platforms))
      (is (contains? capabilities :services)))))

(deftest test-mm3-full-resource-query-flow
  (testing "Complete resource query flow"
    ;; Query resources
    (let [res-response (mm3/query-resources test-endpoint)]
      (is (:success? res-response))
      (is (pos? (:cpu-cores (:data res-response))))
      (is (pos? (:memory-gb (:data res-response)))))

    ;; Use convenience function
    (let [resources (mm3/get-resources test-endpoint)]
      (is (some? resources))
      (is (contains? resources :cpu-cores))
      (is (contains? resources :memory-gb)))))

;;
;; Application Lifecycle Tests
;;

(deftest test-mm3-app-instance-creation
  (testing "Create application instance via Mm3"
    (let [app-desc {:name "test-app"
                   :image "nginx:latest"
                   :resources {:cpu 2 :memory 4}}
          create-response (mm3/create-app-instance test-endpoint app-desc)]
      (is (:success? create-response))
      (is (= 201 (:status create-response)))
      (let [app-id (:id (:data create-response))]
        (is (some? app-id))
        (is (some? (:operationId (:data create-response))))

        ;; Query app instance
        (let [get-response (mm3/get-app-instance test-endpoint app-id)]
          (is (:success? get-response))
          (is (= "test-app" (:name (:data get-response))))
          (is (= "INSTANTIATED" (:instantiationState (:data get-response))))
          (is (= "STARTED" (:operationalState (:data get-response)))))

        ;; Delete app instance
        (let [delete-response (mm3/delete-app-instance test-endpoint app-id)]
          (is (:success? delete-response))
          (is (= 204 (:status delete-response))))

        ;; Verify deletion
        (let [get-after-delete (mm3/get-app-instance test-endpoint app-id)]
          (is (not (:success? get-after-delete)))
          (is (= 404 (:status get-after-delete))))))))

(deftest test-mm3-list-app-instances
  (testing "List application instances via Mm3"
    ;; Initially empty
    (let [list-response (mm3/list-app-instances test-endpoint)]
      (is (:success? list-response))
      (is (empty? (:instances (:data list-response)))))

    ;; Create two instances
    (mm3/create-app-instance test-endpoint {:name "app-1"})
    (mm3/create-app-instance test-endpoint {:name "app-2"})

    ;; List should show both
    (let [list-response (mm3/list-app-instances test-endpoint)]
      (is (:success? list-response))
      (is (= 2 (count (:instances (:data list-response))))))))

(deftest test-mm3-operate-app-instance
  (testing "Operate application instance via Mm3"
    (let [create-response (mm3/create-app-instance test-endpoint {:name "app-operate"})
          app-id (:id (:data create-response))]
      (is (:success? create-response))
      (let [stop-response (mm3/operate-app-instance test-endpoint app-id "STOPPED")]
        (is (:success? stop-response))
        (is (= 200 (:status stop-response)))
        (is (some? (:operationId (:data stop-response))))
        (is (= "INSTANTIATED" (:instantiationState (:data stop-response))))
        (is (= "STOPPED" (:operationalState (:data stop-response)))))
      (let [start-response (mm3/operate-app-instance test-endpoint app-id "STARTED")]
        (is (:success? start-response))
        (is (= 200 (:status start-response)))
        (is (some? (:operationId (:data start-response))))
        (is (= "INSTANTIATED" (:instantiationState (:data start-response))))
        (is (= "STARTED" (:operationalState (:data start-response))))))))

(deftest test-mm3-get-operation
  (testing "Query southbound lifecycle operation status via Mm3"
    (let [create-response (mm3/create-app-instance test-endpoint {:name "app-op-status"})
          create-op-id    (:operationId (:data create-response))
          app-id          (:id (:data create-response))
          operate-response (mm3/operate-app-instance test-endpoint app-id "STOPPED")
          operate-op-id    (:operationId (:data operate-response))
          get-create-op    (mm3/get-operation test-endpoint create-op-id)
          get-operate-op   (mm3/get-operation test-endpoint operate-op-id)]
      (is (:success? get-create-op))
      (is (= 200 (:status get-create-op)))
      (is (= create-op-id (get-in get-create-op [:data :id])))
      (is (= "PROCESSING" (get-in get-create-op [:data :status])))
      (is (:success? get-operate-op))
      (is (= 200 (:status get-operate-op)))
      (is (= "OPERATE" (get-in get-operate-op [:data :operationType]))))))

(deftest test-mm3-create-subscription
  (testing "Create southbound lifecycle subscription via Mm3"
    (let [response (mm3/create-subscription test-endpoint
                                            {:callbackUri "http://localhost:8200/api/mec/internal/mm3/app_lcm/v1/notifications"
                                             :notificationTypes ["AppInstanceStateChangeNotification"
                                                                 "AppLcmOpOccStateChangeNotification"]})]
      (is (:success? response))
      (is (= 201 (:status response)))
      (is (some? (get-in response [:data :id])))
      (is (= "http://localhost:8200/api/mec/internal/mm3/app_lcm/v1/notifications"
             (get-in response [:data :callbackUri]))))))

(deftest test-mock-mepm-emits-notification
  (testing "Mock MEPM can emit a stored lifecycle notification to a callback URI"
    (let [received      (promise)
          callback-port (with-open [socket (java.net.ServerSocket. 0)]
                          (.getLocalPort socket))
          callback-uri  (str "http://localhost:" callback-port "/callback")
          server        (jetty/run-jetty (wrap-json-body
                                          (fn [request]
                                            (deliver received (:body request))
                                            {:status 204 :body ""})
                                          {:keywords? true})
                                         {:port callback-port :join? false})]
      (try
        (let [subscription (mm3/create-subscription test-endpoint
                                                    {:callbackUri callback-uri
                                                     :notificationTypes ["AppLcmOpOccStateChangeNotification"]})
              subscription-id (get-in subscription [:data :id])
              response        (http/post (str test-endpoint "/mm3/test/emit-notification")
                                         {:body             (json/write-value-as-string
                                                              {:subscriptionId subscription-id
                                                               :notification {:notificationType "AppLcmOpOccStateChangeNotification"
                                                                              :subscriptionId   subscription-id
                                                                              :operationId      "op-emit-1"
                                                                              :operationState   "COMPLETED"}})
                                          :content-type     :json
                                          :accept           :json
                                          :throw-exceptions false
                                          :as               :json
                                          :coerce           :always})]
          (is (:success? subscription))
          (is (= 202 (:status response)))
          (is (= 204 (get-in response [:body :callbackStatus])))
          (is (= "AppLcmOpOccStateChangeNotification" (:notificationType @received)))
          (is (= "op-emit-1" (:operationId @received))))
        (finally
          (.stop server))))))

;;
;; HTTP Options Tests
;;

(deftest test-mm3-custom-timeouts
  (testing "Custom timeout options work"
    (let [response (mm3/check-health test-endpoint
                                      {:timeout 5000
                                       :connect-timeout 2000})]
      (is (:success? response)))))

(deftest test-mm3-insecure-option
  (testing "Insecure SSL option works"
    (let [response (mm3/check-health test-endpoint
                                      {:insecure? true})]
      (is (:success? response)))))

;;
;; Performance Tests
;;

(deftest test-mm3-concurrent-requests
  (testing "Handle concurrent requests correctly"
    (let [futures (doall
                   (for [i (range 10)]
                     (future
                       (mm3/check-health test-endpoint))))]
      (let [results (map deref futures)]
        (is (every? :success? results))
        (is (= 10 (count results)))))))

(deftest test-mm3-request-counting
  (testing "Mock server counts requests correctly"
    (mock-mepm/reset-state!)
    (mm3/check-health test-endpoint)
    (mm3/query-capabilities test-endpoint)
    (mm3/query-resources test-endpoint)
    (let [state       (mock-mepm/get-state)
          request-log (mock-mepm/get-request-log)]
      (is (= 3 (:request-count state)))
      (is (= 3 (count request-log)))
      (is (= ["api-server" "api-server" "api-server"]
             (mapv :caller request-log)))
      (is (= ["health" "capabilities" "resources"]
             (mapv :operation request-log))))))

(deftest test-mm3-request-log-can-be-cleared
  (testing "Mock request log is separately inspectable and resettable"
    (mock-mepm/reset-state!)
    (mm3/query-capabilities test-endpoint)
    (is (= 1 (count (mock-mepm/get-request-log))))
    (mock-mepm/clear-request-log!)
    (is (empty? (mock-mepm/get-request-log)))))

;;
;; Integration with MEPM State
;;

(deftest test-mm3-reflects-mepm-state-changes
  (testing "Mm3 client reflects MEPM state changes"
    ;; Initial state
    (let [initial-response (mm3/query-capabilities test-endpoint)]
      (is (some? (:platforms (:data initial-response)))))

    ;; Modify MEPM state
    (swap! @#'mock-mepm/mepm-state
           assoc :capabilities {:platforms ["new-platform"]
                               :services ["new-service"]})

    ;; Query should reflect changes
    (let [updated-response (mm3/query-capabilities test-endpoint)]
      (is (= ["new-platform"] (:platforms (:data updated-response))))
      (is (= ["new-service"] (:services (:data updated-response)))))))
