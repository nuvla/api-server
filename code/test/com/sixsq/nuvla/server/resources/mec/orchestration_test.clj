(ns com.sixsq.nuvla.server.resources.mec.orchestration-test
  "End-to-end orchestration tests for MEC MEO functionality."
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [jsonista.core :as json]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.mec.mock-mepm-server :as mock-mepm]
    [com.sixsq.nuvla.server.resources.mepm :as mepm]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer [content-type header request session]]))

(def base-uri (str p/service-context mepm/resource-type))
(def test-port 18081)
(def test-endpoint (str "http://localhost:" test-port))

(defn start-mock-mepm-fixture [f]
  (mock-mepm/start-server! test-port)
  (try
    (f)
    (finally
      (mock-mepm/stop-server!))))

(use-fixtures :once ltu/with-test-server-fixture start-mock-mepm-fixture)

(defn reset-mepm-state-fixture [f]
  (mock-mepm/reset-state!)
  (f))

(use-fixtures :each reset-mepm-state-fixture)

(deftest test-complete-mepm-orchestration-flow
  (testing "Complete MEPM lifecycle"
    (let [session-anon  (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header
                                "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")]

      (let [mepm-data {:name         "Orchestration Test MEPM"
                       :description  "End-to-end test MEPM"
                       :endpoint     test-endpoint
                       :capabilities {:platforms ["kubernetes"]
                                      :services  ["mec-service-1"]}}
            resp-create (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-value-as-string mepm-data))
                            (ltu/body->edn)
                            (ltu/is-status 201))
            mepm-id (ltu/body-resource-id resp-create)
            mepm-url (str p/service-context mepm-id)]

        (let [mepm (-> session-admin
                       (request mepm-url)
                       (ltu/body->edn)
                       (ltu/is-status 200)
                       :response
                       :body)]
          (is (= "Orchestration Test MEPM" (:name mepm)))
          (is (= "ONLINE" (:status mepm))))

        ;; Perform health check
        (-> session-admin
            (request (str mepm-url "/check-health")
                     :request-method :post
                     :body (json/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [mepm-after-health (-> session-admin
                                    (request mepm-url)
                                    (ltu/body->edn)
                                    :response
                                    :body)]
          (is (= "ONLINE" (:status mepm-after-health))))

        ;; Query capabilities
        (-> session-admin
            (request (str mepm-url "/query-capabilities")
                     :request-method :post
                     :body (json/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [mepm-with-caps (-> session-admin
                                 (request mepm-url)
                                 (ltu/body->edn)
                                 :response
                                 :body)]
          (is (some? (:capabilities mepm-with-caps)))
          (is (vector? (get-in mepm-with-caps [:capabilities :platforms]))))

        ;; Query resources
        (-> session-admin
            (request (str mepm-url "/query-resources")
                     :request-method :post
                     :body (json/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [mepm-with-res (-> session-admin
                                (request mepm-url)
                                (ltu/body->edn)
                                :response
                                :body)]
          (is (some? (:resources mepm-with-res)))
          (is (pos? (get-in mepm-with-res [:resources :cpu-cores]))))

        (-> session-admin
            (request mepm-url :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-admin
            (request mepm-url)
            (ltu/body->edn)
            (ltu/is-status 404))))))

(deftest test-multiple-mepm-management
  (testing "Register and list multiple MEPMs"
    (let [session-anon  (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header
                                "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")]

      (let [mepm1-data {:name "MEPM-1" :endpoint test-endpoint
                        :capabilities {:platforms ["kubernetes"]}}
            resp1 (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-value-as-string mepm1-data))
                      (ltu/body->edn)
                      (ltu/is-status 201))
            mepm1-id (ltu/body-resource-id resp1)]

        (let [mepm2-data {:name "MEPM-2" :endpoint test-endpoint
                          :capabilities {:platforms ["kubernetes"]}}
              resp2 (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-value-as-string mepm2-data))
                        (ltu/body->edn)
                        (ltu/is-status 201))
              mepm2-id (ltu/body-resource-id resp2)]

          (let [search-resp (-> session-admin
                                (request base-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                mepms (get-in search-resp [:response :body :resources])]
            (is (>= (count mepms) 2)))

          (-> session-admin
              (request (str p/service-context mepm1-id) :request-method :delete)
              (ltu/body->edn))
          (-> session-admin
              (request (str p/service-context mepm2-id) :request-method :delete)
              (ltu/body->edn)))))))

(deftest test-mepm-health-degradation-recovery
  (testing "MEPM health status transitions"
    (let [session-anon  (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header
                                "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")]

      (let [mepm-data {:name "Health Test MEPM" :endpoint test-endpoint
                       :capabilities {:platforms ["kubernetes"]}}
            resp (-> session-admin
                     (request base-uri
                              :request-method :post
                              :body (json/write-value-as-string mepm-data))
                     (ltu/body->edn)
                     (ltu/is-status 201))
            mepm-id (ltu/body-resource-id resp)
            mepm-url (str p/service-context mepm-id)]

        ;; Health check should succeed
        (-> session-admin
            (request (str mepm-url "/check-health")
                     :request-method :post
                     :body (json/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (is (= "ONLINE" (:status (-> session-admin (request mepm-url) (ltu/body->edn) :response :body))))

        ;; Simulate degradation
        (mock-mepm/set-error-mode! :degraded)

        ;; Health check should now fail with 503
        (-> session-admin
            (request (str mepm-url "/check-health")
                     :request-method :post
                     :body (json/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 503))

        (is (= "DEGRADED" (:status (-> session-admin (request mepm-url) (ltu/body->edn) :response :body))))

        ;; Recover from degradation
        (mock-mepm/set-error-mode! nil)

        ;; Health check should succeed again
        (-> session-admin
            (request (str mepm-url "/check-health")
                     :request-method :post
                     :body (json/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (is (= "ONLINE" (:status (-> session-admin (request mepm-url) (ltu/body->edn) :response :body))))

        (-> session-admin
            (request mepm-url :request-method :delete)
            (ltu/body->edn))))))
