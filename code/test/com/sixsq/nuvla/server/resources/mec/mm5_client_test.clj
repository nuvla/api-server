(ns com.sixsq.nuvla.server.resources.mec.mm5-client-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.mec.mm5-client :as mm5]
    [clj-http.client :as http]))


;; Mock HTTP responses
(def mock-health-success
  {:status 200
   :body   {:status "healthy"
            :timestamp "2025-10-21T14:00:00Z"
            :uptime-seconds 86400}})

(def mock-health-failure
  {:status 503
   :body   {:status "unhealthy"
            :message "Service unavailable"}})

(def mock-capabilities-success
  {:status 200
   :body   {:platforms ["x86_64" "arm64"]
            :services ["rnis" "location" "wai"]
            :api-version "3.1.1"}})

(def mock-resources-success
  {:status 200
   :body   {:cpu-cores 64
            :memory-gb 256
            :storage-gb 2000
            :gpu-count 4}})


(deftest test-check-health-success
  (testing "Successful health check"
    (with-redefs [http/get (fn [_url _opts] mock-health-success)]
      (let [result (mm5/check-health "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (:success? result))
        (is (= 200 (:status result)))
        (is (= "healthy" (get-in result [:data :status])))))))


(deftest test-check-health-failure
  (testing "Failed health check returns proper error"
    (with-redefs [http/get (fn [_url _opts] mock-health-failure)]
      (let [result (mm5/check-health "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (not (:success? result)))
        (is (= 503 (:status result)))
        (is (= :server-error (:error result)))))))


(deftest test-check-health-connection-error
  (testing "Connection error during health check"
    (with-redefs [http/get (fn [_url _opts] 
                             (throw (Exception. "Connection refused")))]
      (let [result (mm5/check-health "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (not (:success? result)))
        (is (= :connection-error (:error result)))
        (is (some? (:exception result)))))))


(deftest test-query-capabilities-success
  (testing "Successful capabilities query"
    (with-redefs [http/get (fn [_url _opts] mock-capabilities-success)]
      (let [result (mm5/query-capabilities "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (:success? result))
        (is (= 200 (:status result)))
        (is (= ["x86_64" "arm64"] (get-in result [:data :platforms])))
        (is (= ["rnis" "location" "wai"] (get-in result [:data :services])))))))


(deftest test-query-capabilities-failure
  (testing "Failed capabilities query"
    (with-redefs [http/get (fn [_url _opts] {:status 404 :body {:message "Not found"}})]
      (let [result (mm5/query-capabilities "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (not (:success? result)))
        (is (= 404 (:status result)))
        (is (= :client-error (:error result)))))))


(deftest test-query-resources-success
  (testing "Successful resources query"
    (with-redefs [http/get (fn [_url _opts] mock-resources-success)]
      (let [result (mm5/query-resources "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (:success? result))
        (is (= 200 (:status result)))
        (is (= 64 (get-in result [:data :cpu-cores])))
        (is (= 256 (get-in result [:data :memory-gb])))
        (is (= 4 (get-in result [:data :gpu-count])))))))


(deftest test-configure-platform-success
  (testing "Successful platform configuration"
    (with-redefs [http/post (fn [_url _opts] {:status 200 :body {:status "configured"}})]
      (let [config {:service-registry true :traffic-rules false}
            result (mm5/configure-platform "https://mepm.example.com:8443" config {:retry-attempts 1})]
        (is (:success? result))
        (is (= 200 (:status result)))))))


(deftest test-get-platform-info-success
  (testing "Successful platform info retrieval"
    (with-redefs [http/get (fn [_url _opts] 
                             {:status 200 
                              :body {:name "MEPM-001"
                                     :version "1.0.0"
                                     :location "edge-site-1"
                                     :status "ONLINE"}})]
      (let [result (mm5/get-platform-info "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (:success? result))
        (is (= "MEPM-001" (get-in result [:data :name])))
        (is (= "ONLINE" (get-in result [:data :status])))))))


(deftest test-retry-mechanism
  (testing "Retry mechanism on transient failures"
    (let [call-count (atom 0)]
      (with-redefs [http/get (fn [_url _opts]
                               (swap! call-count inc)
                               (if (< @call-count 3)
                                 (throw (Exception. "Transient error"))
                                 mock-health-success))]
        (let [result (mm5/check-health "https://mepm.example.com:8443" {:retry-attempts 3})]
          (is (:success? result))
          (is (= 3 @call-count) "Should retry exactly 3 times"))))))


(deftest test-healthy-predicate
  (testing "healthy? convenience function"
    (with-redefs [http/get (fn [_url _opts] mock-health-success)]
      (is (true? (mm5/healthy? "https://mepm.example.com:8443" {:retry-attempts 1}))))
    
    (with-redefs [http/get (fn [_url _opts] mock-health-failure)]
      (is (false? (mm5/healthy? "https://mepm.example.com:8443" {:retry-attempts 1}))))))


(deftest test-get-capabilities-convenience
  (testing "get-capabilities convenience function returns data or nil"
    (with-redefs [http/get (fn [_url _opts] mock-capabilities-success)]
      (let [caps (mm5/get-capabilities "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (some? caps))
        (is (= ["x86_64" "arm64"] (:platforms caps)))))
    
    (with-redefs [http/get (fn [_url _opts] {:status 500 :body {}})]
      (is (nil? (mm5/get-capabilities "https://mepm.example.com:8443" {:retry-attempts 1}))))))


(deftest test-get-resources-convenience
  (testing "get-resources convenience function returns data or nil"
    (with-redefs [http/get (fn [_url _opts] mock-resources-success)]
      (let [resources (mm5/get-resources "https://mepm.example.com:8443" {:retry-attempts 1})]
        (is (some? resources))
        (is (= 64 (:cpu-cores resources)))))
    
    (with-redefs [http/get (fn [_url _opts] {:status 500 :body {}})]
      (is (nil? (mm5/get-resources "https://mepm.example.com:8443" {:retry-attempts 1}))))))


(deftest test-url-construction
  (testing "URLs are correctly constructed"
    (let [captured-urls (atom [])]
      (with-redefs [http/get (fn [url _opts] 
                               (swap! captured-urls conj url)
                               mock-health-success)]
        (mm5/check-health "https://mepm.example.com:8443" {:retry-attempts 1})
        (mm5/query-capabilities "https://mepm.example.com:8443" {:retry-attempts 1})
        (mm5/query-resources "https://mepm.example.com:8443" {:retry-attempts 1})
        (mm5/get-platform-info "https://mepm.example.com:8443" {:retry-attempts 1})
        
        (is (= "https://mepm.example.com:8443/mm5/health" (first @captured-urls)))
        (is (= "https://mepm.example.com:8443/mm5/capabilities" (second @captured-urls)))
        (is (= "https://mepm.example.com:8443/mm5/resources" (nth @captured-urls 2)))
        (is (= "https://mepm.example.com:8443/mm5/platform-info" (nth @captured-urls 3)))))))


(deftest test-http-options
  (testing "HTTP options are properly configured"
    (let [captured-opts (atom nil)]
      (with-redefs [http/get (fn [_url opts] 
                               (reset! captured-opts opts)
                               mock-health-success)]
        (mm5/check-health "https://mepm.example.com:8443" 
                          {:timeout 60000
                           :connect-timeout 20000
                           :insecure? true
                           :retry-attempts 1})
        
        (is (= 60000 (:socket-timeout @captured-opts)))
        (is (= 20000 (:connection-timeout @captured-opts)))
        (is (true? (:insecure? @captured-opts)))
        (is (= :json (:as @captured-opts)))
        (is (= :json (:content-type @captured-opts)))))))
