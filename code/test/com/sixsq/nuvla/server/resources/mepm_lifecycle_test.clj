(ns com.sixsq.nuvla.server.resources.mepm-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
    [com.sixsq.nuvla.server.resources.mepm :as mepm]
    [jsonista.core :as json]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context mepm/resource-type))


(def valid-mepm
  {:name          "Test MEPM"
   :description   "Test MEC Platform Manager"
   :endpoint      "https://mepm.example.com:8443"
   :capabilities  {:platforms    ["x86_64" "arm64"]
                   :services     ["mec-service-1" "mec-service-2"]
                   :api-version  "v2"}
   :resources     {:cpu-cores   64
                   :memory-gb   256
                   :storage-gb  2048
                   :gpu-count   8}
   :status        "ONLINE"
   :mec-host-id   "nuvlabox/test-host-123"
   :credential-id "credential/test-credential-456"
   :version       "2.1.0"
   :tags          ["production" "edge"]})


(deftest lifecycle
  ;; Mock Mm3 client responses for testing
  (with-redefs [mm3/check-health (fn [_endpoint & [_opts]]
                                    {:success? true
                                     :status 200
                                     :data {:status "healthy" :uptime-seconds 86400}})
                mm3/query-capabilities (fn [_endpoint & [_opts]]
                                         {:success? true
                                          :status 200
                                          :data {:platforms ["x86_64" "arm64"]
                                                 :services ["mec-service-1" "mec-service-2"]
                                                 :api-version "v2"}})
                mm3/query-resources (fn [_endpoint & [_opts]]
                                      {:success? true
                                       :status 200
                                       :data {:cpu-cores 64
                                              :memory-gb 256
                                              :storage-gb 1000
                                              :gpu-count 2}})
                mm3/create-subscription (fn [_endpoint payload & [_opts]]
                                          {:success? true
                                           :status 201
                                           :data {:id "mm3-sub-123"
                                                  :callbackUri (:callbackUri payload)}})]
    (let [session-anon  (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header
                                "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session-anon authn-info-header
                                "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    ;; Anonymous query should fail
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; Admin query should succeed but have no MEPMs initially
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?))

    ;; User query should succeed
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?))

    ;; Anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-value-as-string valid-mepm))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; User create should succeed
    (let [resp    (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-value-as-string valid-mepm))
                      (ltu/body->edn)
                      (ltu/is-status 201))
          id      (ltu/body-resource-id resp)
          location (ltu/location resp)
          uri     (str p/service-context id)]

      ;; Check created resource
      (let [mepm (-> session-user
                     (request uri)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/body))]
        (is (= "Test MEPM" (:name mepm)))
        (is (= "https://mepm.example.com:8443" (:endpoint mepm)))
        (is (= "ONLINE" (:status mepm)))
        (is (= ["x86_64" "arm64"] (get-in mepm [:capabilities :platforms])))
        (is (= 64 (get-in mepm [:resources :cpu-cores])))
        (is (= "mm3-sub-123" (:mm3-subscription-id mepm)))
        (is (re-find #"/api/mec/internal/mm3/app_lcm/v1/notifications$"
                     (:mm3-subscription-callback-uri mepm)))
        (is (:created mepm))
        (is (:updated mepm)))

      ;; Duplicate endpoint create should fail, even with trailing slash variation
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-value-as-string (assoc valid-mepm :endpoint "https://mepm.example.com:8443/")))
          (ltu/body->edn)
          (ltu/is-status 409))

      ;; Update MEPM status
      (let [updated-mepm (-> session-user
                             (request uri
                                      :request-method :put
                                      :body (json/write-value-as-string {:status "DEGRADED"}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/body))]
        (is (= "DEGRADED" (:status updated-mepm))))

      ;; Check available operations
      (let [ops (-> session-user
                    (request uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/body)
                    :operations)]
        (is (some #(= "check-health" (:rel %)) ops))
        (is (some #(= "query-capabilities" (:rel %)) ops))
        (is (some #(= "query-resources" (:rel %)) ops))
        (is (some #(= "ensure-lifecycle-subscription" (:rel %)) ops)))

      ;; Test check-health action
      (-> session-user
          (request (str uri "/check-health")
                   :request-method :post
                   :body (json/write-value-as-string {}))
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; Verify last-check was updated
      (let [mepm-after-check (-> session-user
                                 (request uri)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))]
        (is (:last-check mepm-after-check)))

      ;; Test query-capabilities action
      (let [resp (-> session-user
                     (request (str uri "/query-capabilities")
                              :request-method :post
                              :body (json/write-value-as-string {}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/body))]
        (is (= ["x86_64" "arm64"] (get-in resp [:message :platforms]))))

      ;; Test query-resources action
      (let [resp (-> session-user
                     (request (str uri "/query-resources")
                              :request-method :post
                              :body (json/write-value-as-string {}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/body))]
        (is (= 64 (get-in resp [:message :cpu-cores]))))

      ;; Test ensure-lifecycle-subscription action is idempotent
      (let [resp (-> session-user
                     (request (str uri "/ensure-lifecycle-subscription")
                              :request-method :post
                              :body (json/write-value-as-string {}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/body))]
        (is (= "mm3-sub-123" (get-in resp [:message :subscriptionId]))))

      ;; Delete MEPM
      (-> session-user
          (request uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; Verify deletion
      (-> session-user
          (request uri)
          (ltu/body->edn)
          (ltu/is-status 404))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id mepm/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
