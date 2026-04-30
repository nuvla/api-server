(ns com.sixsq.nuvla.server.resources.mec.app-package-lifecycle-test
  "Integration tests for ETSI MEC 010-2 Mm1 Application Package Lifecycle"
  (:require
    [cheshire.core :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module-application-mec :as mec]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context "mec/app_lcm/v2"))


(deftest mm1-app-package-lifecycle
  (let [session-admin (-> (ltu/ring-app)
                          session
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-anon"))
        session-user (-> (ltu/ring-app)
                         session
                         (content-type "application/json")
                         (header authn-info-header "user/test-user user/test-user group/nuvla-anon"))]
    
    (testing "POST /app_packages - Create MEC application package"
      (let [create-request {:appPkgName "test-mec-app"
                            :appPkgVersion "1.0.0"
                            :appProvider "Acme Corp"
                            :userDefinedData {:environment "testing"
                                              :cost-center "R&D"}}
            response (-> session-admin
                         (request (str base-uri "/app_packages")
                                  :request-method :post
                                  :body (json/generate-string create-request))
                         :response)
            body (when (:body response)
                   (json/parse-string (:body response) true))]
        
        (is (= 201 (:status response)) (pr-str body))
        (is (string? (get-in response [:headers "Location"])))
        (is (= "test-mec-app" (:appName body)))
        (is (= "Acme Corp" (:appProvider body)))
        (is (= "1.0.0" (:appSoftVersion body)))
        (is (= "ONBOARDED" (:onboardingState body)))
        (is (= "ENABLED" (:operationalState body)))
        (is (= "NOT_IN_USE" (:usageState body)))
        (is (map? (:_links body)))
        (is (= (:appPkgId body) (:appDId body)))
        
        ;; Store package ID for subsequent tests
        (def app-pkg-id (:appPkgId body))
        (def created-module-id (:id body))
        
        (testing "GET /app_packages/{appPkgId} - Retrieve created package"
          (let [get-response (-> session-admin
                                 (request (str base-uri "/app_packages/" app-pkg-id)
                                          :request-method :get)
                                 :response)
                get-body (json/parse-string (:body get-response) true)]
            
            (is (= 200 (:status get-response)))
            (is (= app-pkg-id (:appPkgId get-body)))
            (is (= "test-mec-app" (:appName get-body)))
            (is (= "ONBOARDED" (:onboardingState get-body)))))

        (testing "GET /app_packages/{appPkgId}/appD - Retrieve AppD descriptor"
          (let [appd-response (-> session-admin
                                  (request (str base-uri "/app_packages/" app-pkg-id "/appD")
                                           :request-method :get)
                                  :response)
                appd-body (json/parse-string (:body appd-response) true)]

            (is (= 200 (:status appd-response)))
            (is (= app-pkg-id (:appDId appd-body)))
            (is (= "test-mec-app" (:appName appd-body)))
            (is (re-find #"application/json" (get-in appd-response [:headers "Content-Type"] "")))))

        (testing "GET /app_packages/{appPkgId}/package_content - Retrieve package content"
          (let [content-response (-> session-admin
                                     (request (str base-uri "/app_packages/" app-pkg-id "/package_content")
                                              :request-method :get)
                                     :response)
                content-body (json/parse-string (:body content-response) true)]

            (is (= 200 (:status content-response)))
            (is (= app-pkg-id (:appDId content-body)))
            (is (= "test-mec-app" (:appName content-body)))
            (is (re-find #"application/json" (get-in content-response [:headers "Content-Type"] "")))
            (is (string? (get-in content-response [:headers "Content-Disposition"])))))

        (testing "PUT /app_packages/{appPkgId}/package_content - Update package descriptor"
          (let [updated-content {:appName "updated-mec-app"
                                 :appDescription "Updated descriptor"
                                 :appProvider "Acme Corp"
                                 :appSoftVersion "1.1.0"
                                 :appDVersion "3.2.1"
                                 :mecVersion "2.2.1"
                                 :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 2}
                                                            :virtualMemory {:virtualMemSize 2048}}
                                 :swImageDescriptor [{:swImageName "updated-mec-app"
                                                      :swImageVersion "1.1.0"
                                                      :containerFormat :DOCKER
                                                      :swImage "sixsq/updated-mec-app:1.1.0"}]
                                 :virtualStorageDescriptor []
                                 :appExtCpd []
                                 :appServiceRequired []
                                 :trafficRuleDescriptor []
                                 :dnsRuleDescriptor []
                                 :appFeatureRequired []}
                put-response (-> session-admin
                                 (request (str base-uri "/app_packages/" app-pkg-id "/package_content")
                                          :request-method :put
                                          :body (json/generate-string updated-content))
                                 :response)
                get-response (-> session-admin
                                 (request (str base-uri "/app_packages/" app-pkg-id "/package_content")
                                          :request-method :get)
                                 :response)
                get-body (json/parse-string (:body get-response) true)]
            (is (= 204 (:status put-response)))
            (is (= app-pkg-id (:appDId get-body)))
            (is (= "updated-mec-app" (:appName get-body)))
            (is (= "1.1.0" (:appSoftVersion get-body)))))
        
        (testing "GET /app_packages - Query all packages"
          (let [query-response (-> session-admin
                                   (request (str base-uri "/app_packages")
                                            :request-method :get)
                                   :response)
                query-body (json/parse-string (:body query-response) true)
                packages (:AppPkgInfo query-body)]
            
            (is (= 200 (:status query-response)))
            (is (vector? packages))
            (is (pos? (count packages)))
            (is (some #(= app-pkg-id (:appPkgId %)) packages))))
        
        (testing "GET /app_packages?appName=updated-mec-app - Filter by name"
          (let [query-response (-> session-admin
                                   (request (str base-uri "/app_packages?appName=updated-mec-app")
                                            :request-method :get)
                                   :response)
                query-body (json/parse-string (:body query-response) true)
                packages (:AppPkgInfo query-body)]
            
            (is (= 200 (:status query-response)))
            (is (= 1 (count packages)))
            (is (= "updated-mec-app" (:appName (first packages))))))
        
        (testing "GET /app_packages?appProvider=Acme Corp - Filter by provider"
          (let [query-response (-> session-admin
                                   (request (str base-uri "/app_packages?appProvider=Acme%20Corp")
                                            :request-method :get)
                                   :response)
                query-body (json/parse-string (:body query-response) true)
                packages (:AppPkgInfo query-body)]
            
            (is (= 200 (:status query-response)))
            (is (some #(= "Acme Corp" (:appProvider %)) packages))))
        
        (testing "DELETE /app_packages/{appPkgId} - Delete package"
          (let [delete-response (-> session-admin
                                    (request (str base-uri "/app_packages/" app-pkg-id)
                                             :request-method :delete)
                                    :response)]
            
            (is (= 204 (:status delete-response)))))
        
        (testing "GET /app_packages/{appPkgId} - Verify deletion (404)"
          (let [get-response (-> session-admin
                                 (request (str base-uri "/app_packages/" app-pkg-id)
                                          :request-method :get)
                                 :response)]
            
            (is (= 404 (:status get-response)))))))))


(deftest mm1-app-package-validation
  (let [session-admin (-> (ltu/ring-app)
                          session
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-anon"))]
    
    (testing "POST /app_packages without appPkgName - Bad Request"
      (let [invalid-request {:appPkgVersion "1.0.0"}
            response (-> session-admin
                         (request (str base-uri "/app_packages")
                                  :request-method :post
                                  :body (json/generate-string invalid-request))
                         :response)
            body (when (:body response)
                   (json/parse-string (:body response) true))]
        
        (is (= 400 (:status response)))
        (is (= 400 (:status body)))
        (is (string? (:detail body)))
        (is (.contains (:detail body) "appPkgName"))))
    
    (testing "GET /app_packages/{nonexistent} - Not Found"
      (let [response (-> session-admin
                         (request (str base-uri "/app_packages/module/nonexistent-123")
                                  :request-method :get)
                         :response)
            body (when (:body response)
                   (json/parse-string (:body response) true))]
        
        (is (= 404 (:status response)))
        (is (= 404 (:status body)))
        (is (= "Not Found" (:title body)))))
    
    (testing "DELETE /app_packages/{nonexistent} - Not Found"
      (let [response (-> session-admin
                         (request (str base-uri "/app_packages/module/nonexistent-456")
                                  :request-method :delete)
                         :response)]
        
        (is (= 404 (:status response)))))

    (testing "PUT /app_packages/{nonexistent}/package_content - Not Found"
      (let [response (-> session-admin
                         (request (str base-uri "/app_packages/module/nonexistent-789/package_content")
                                  :request-method :put
                                  :body (json/generate-string {:appName "missing"}))
                         :response)]
        (is (= 404 (:status response)))))))


(deftest mm1-mec-module-integration
  (let [session-admin (-> (ltu/ring-app)
                          session
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-anon"))]
    
    (testing "Create MEC module and verify it appears in Mm1 query"
      ;; Create parent project first, then create a module-application-mec directly
      (let [project-response (-> session-admin
                                 (request (str p/service-context "module")
                                          :request-method :post
                                          :body (json/generate-string {:subtype "project"
                                                                       :path "test-mec-modules"}))
                                 :response)
            mec-module {:name "Direct MEC Module"
                        :description "MEC module created directly"
                        :subtype "application_mec"
                        :path "test-mec-modules/direct"
                        :parent-path "test-mec-modules"
                        :published false
                        :content {:appName "Direct MEC App"
                                  :appDescription "Direct MEC App description"
                                  :appDId (str "module/" (random-uuid))
                                  :appProvider "Test Provider"
                                  :appSoftVersion "2.0.0"
                                  :appDVersion "3.2.1"
                                  :mecVersion "2.2.1"
                                  :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 2}
                                                             :virtualMemory {:virtualMemSize 2048}}
                                  :swImageDescriptor [{:swImageName "direct-mec-app"
                                                       :swImageVersion "2.0.0"
                                                       :containerFormat :DOCKER
                                                       :swImage "sixsq/direct-mec-app:2.0.0"}]
                                  :virtualStorageDescriptor []
                                  :appExtCpd []
                                  :appServiceRequired []
                                  :trafficRuleDescriptor []
                                  :dnsRuleDescriptor []
                                  :appFeatureRequired []}}
            create-response (-> session-admin
                                (request (str p/service-context "module")
                                         :request-method :post
                                         :body (json/generate-string mec-module))
                                :response)
            create-body (when (:body create-response)
                          (json/parse-string (:body create-response) true))
            module-id (:resource-id create-body)]
        
        (is (= 201 (:status project-response)))
        (is (= 201 (:status create-response))) ; direct module path kept for subtype smoke coverage
        (is (string? module-id))
        
        ;; Query via Mm1 API
        (let [query-response (-> session-admin
                                 (request (str base-uri "/app_packages?appName=Direct%20MEC%20App")
                                          :request-method :get)
                                 :response)
              query-body (json/parse-string (:body query-response) true)
              packages (:AppPkgInfo query-body)]
          
          (is (= 200 (:status query-response)))
          (is (some #(= "Direct MEC App" (:appName %)) packages)))
        
        ;; Cleanup
        (-> session-admin
            (request (str base-uri "/app_packages/" module-id)
                     :request-method :delete))))))
