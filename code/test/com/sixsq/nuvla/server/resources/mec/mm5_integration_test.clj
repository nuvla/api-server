(ns com.sixsq.nuvla.server.resources.mec.mm5-integration-test
  "Integration tests for Mm5 interface using mock MEPM server."
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.mec.mm5-client :as mm5]
    [com.sixsq.nuvla.server.resources.mec.mock-mepm-server :as mock-mepm]))

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
;; Mm5 Protocol Validation Tests
;;

(deftest test-mm5-health-check-protocol
  (testing "Health check follows ETSI MEC 003 protocol"
    (let [response (mm5/check-health test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :status))
      (is (contains? (:data response) :timestamp))
      (is (contains? (:data response) :version))
      (is (contains? (:data response) :checks)))))

(deftest test-mm5-capabilities-protocol
  (testing "Capabilities query follows ETSI MEC 003 protocol"
    (let [response (mm5/query-capabilities test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :platforms))
      (is (contains? (:data response) :services))
      (is (contains? (:data response) :api-version))
      (is (vector? (:platforms (:data response))))
      (is (vector? (:services (:data response)))))))

(deftest test-mm5-resources-protocol
  (testing "Resources query follows ETSI MEC 003 protocol"
    (let [response (mm5/query-resources test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :cpu-cores))
      (is (contains? (:data response) :memory-gb))
      (is (contains? (:data response) :storage-gb))
      (is (pos? (:cpu-cores (:data response))))
      (is (pos? (:memory-gb (:data response)))))))

(deftest test-mm5-platform-info-protocol
  (testing "Platform info follows ETSI MEC 003 protocol"
    (let [response (mm5/get-platform-info test-endpoint)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :platform-id))
      (is (contains? (:data response) :platform-name))
      (is (contains? (:data response) :location)))))

(deftest test-mm5-configure-platform-protocol
  (testing "Platform configuration follows ETSI MEC 003 protocol"
    (let [config {:dns-rules [{:domain "example.com" :ip "10.0.0.1"}]
                  :traffic-rules [{:priority 1 :action "allow"}]}
          response (mm5/configure-platform test-endpoint config)]
      (is (:success? response))
      (is (= 200 (:status response)))
      (is (contains? (:data response) :success))
      (is (:success (:data response))))))

;;
;; Error Handling Tests
;;

(deftest test-mm5-timeout-handling
  (testing "Graceful handling of MEPM timeout"
    (mock-mepm/set-error-mode! :timeout)
    (let [response (mm5/check-health test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 504 (:status response))))))

(deftest test-mm5-server-error-handling
  (testing "Graceful handling of MEPM server error"
    (mock-mepm/set-error-mode! :server-error)
    (let [response (mm5/query-capabilities test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 500 (:status response))))))

(deftest test-mm5-not-found-handling
  (testing "Graceful handling of MEPM not found"
    (mock-mepm/set-error-mode! :not-found)
    (let [response (mm5/query-resources test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 404 (:status response))))))

(deftest test-mm5-degraded-service
  (testing "Graceful handling of degraded MEPM"
    (mock-mepm/set-error-mode! :degraded)
    (let [response (mm5/check-health test-endpoint {:retry-attempts 1})]
      (is (not (:success? response)))
      (is (= 503 (:status response))))))

;;
;; Retry Mechanism Tests
;;

(deftest test-mm5-retry-mechanism-simulation
  (testing "Mock server can simulate transient failures"
    ;; This test verifies the mock server error mode works correctly
    ;; Actual retry testing is done implicitly in other tests
    (mock-mepm/set-error-mode! :server-error)
    (let [response1 (mm5/check-health test-endpoint {:retry-attempts 1})]
      (is (not (:success? response1))))
    
    ;; Reset error mode and verify recovery
    (mock-mepm/set-error-mode! nil)
    (let [response2 (mm5/check-health test-endpoint)]
      (is (:success? response2)))))

(deftest test-mm5-connection-refused
  (testing "Graceful handling of connection refused"
    (let [bad-endpoint "http://localhost:9999"]
      (let [response (mm5/check-health bad-endpoint {:retry-attempts 1
                                                      :connect-timeout 1000})]
        (is (not (:success? response)))
        (is (= :connection-error (:error response)))))))

;;
;; End-to-End Flow Tests
;;

(deftest test-mm5-full-health-check-flow
  (testing "Complete health check flow"
    ;; Check health
    (let [health-response (mm5/check-health test-endpoint)]
      (is (:success? health-response))
      (is (= "online" (:status (:data health-response)))))

    ;; Use convenience function
    (is (mm5/healthy? test-endpoint))))

(deftest test-mm5-full-capability-query-flow
  (testing "Complete capability query flow"
    ;; Query capabilities
    (let [cap-response (mm5/query-capabilities test-endpoint)]
      (is (:success? cap-response))
      (is (seq (:platforms (:data cap-response))))
      (is (seq (:services (:data cap-response)))))

    ;; Use convenience function
    (let [capabilities (mm5/get-capabilities test-endpoint)]
      (is (some? capabilities))
      (is (contains? capabilities :platforms))
      (is (contains? capabilities :services)))))

(deftest test-mm5-full-resource-query-flow
  (testing "Complete resource query flow"
    ;; Query resources
    (let [res-response (mm5/query-resources test-endpoint)]
      (is (:success? res-response))
      (is (pos? (:cpu-cores (:data res-response))))
      (is (pos? (:memory-gb (:data res-response)))))

    ;; Use convenience function
    (let [resources (mm5/get-resources test-endpoint)]
      (is (some? resources))
      (is (contains? resources :cpu-cores))
      (is (contains? resources :memory-gb)))))

;;
;; Application Lifecycle Tests
;;

(deftest test-mm5-app-instance-creation
  (testing "Create application instance via Mm5"
    (let [app-desc {:name "test-app"
                   :image "nginx:latest"
                   :resources {:cpu 2 :memory 4}}
          create-response (mm5/create-app-instance test-endpoint app-desc)]
      (is (:success? create-response))
      (is (= 201 (:status create-response)))
      (let [app-id (:id (:data create-response))]
        (is (some? app-id))

        ;; Query app instance
        (let [get-response (mm5/get-app-instance test-endpoint app-id)]
          (is (:success? get-response))
          (is (= "test-app" (:name (:data get-response))))
          (is (= "INSTANTIATED" (:status (:data get-response)))))

        ;; Delete app instance
        (let [delete-response (mm5/delete-app-instance test-endpoint app-id)]
          (is (:success? delete-response))
          (is (= 204 (:status delete-response))))

        ;; Verify deletion
        (let [get-after-delete (mm5/get-app-instance test-endpoint app-id)]
          (is (not (:success? get-after-delete)))
          (is (= 404 (:status get-after-delete))))))))

(deftest test-mm5-list-app-instances
  (testing "List application instances via Mm5"
    ;; Initially empty
    (let [list-response (mm5/list-app-instances test-endpoint)]
      (is (:success? list-response))
      (is (empty? (:instances (:data list-response)))))

    ;; Create two instances
    (mm5/create-app-instance test-endpoint {:name "app-1"})
    (mm5/create-app-instance test-endpoint {:name "app-2"})

    ;; List should show both
    (let [list-response (mm5/list-app-instances test-endpoint)]
      (is (:success? list-response))
      (is (= 2 (count (:instances (:data list-response))))))))

;;
;; HTTP Options Tests
;;

(deftest test-mm5-custom-timeouts
  (testing "Custom timeout options work"
    (let [response (mm5/check-health test-endpoint
                                      {:timeout 5000
                                       :connect-timeout 2000})]
      (is (:success? response)))))

(deftest test-mm5-insecure-option
  (testing "Insecure SSL option works"
    (let [response (mm5/check-health test-endpoint
                                      {:insecure? true})]
      (is (:success? response)))))

;;
;; Performance Tests
;;

(deftest test-mm5-concurrent-requests
  (testing "Handle concurrent requests correctly"
    (let [futures (doall
                   (for [i (range 10)]
                     (future
                       (mm5/check-health test-endpoint))))]
      (let [results (map deref futures)]
        (is (every? :success? results))
        (is (= 10 (count results)))))))

(deftest test-mm5-request-counting
  (testing "Mock server counts requests correctly"
    (mock-mepm/reset-state!)
    (mm5/check-health test-endpoint)
    (mm5/query-capabilities test-endpoint)
    (mm5/query-resources test-endpoint)
    (let [state (mock-mepm/get-state)]
      (is (= 3 (:request-count state))))))

;;
;; Integration with MEPM State
;;

(deftest test-mm5-reflects-mepm-state-changes
  (testing "Mm5 client reflects MEPM state changes"
    ;; Initial state
    (let [initial-response (mm5/query-capabilities test-endpoint)]
      (is (some? (:platforms (:data initial-response)))))

    ;; Modify MEPM state
    (swap! @#'mock-mepm/mepm-state
           assoc :capabilities {:platforms ["new-platform"]
                               :services ["new-service"]})

    ;; Query should reflect changes
    (let [updated-response (mm5/query-capabilities test-endpoint)]
      (is (= ["new-platform"] (:platforms (:data updated-response))))
      (is (= ["new-service"] (:services (:data updated-response)))))))
