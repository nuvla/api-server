(ns com.sixsq.nuvla.server.resources.mec.app-package-lifecycle-test
  "Integration tests for ETSI MEC 010-2 Mm1 Application Package Lifecycle"
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module-application-mec :as mec]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context "/api/mec/app_lcm/v2"))


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
                                  :body (json/write-str create-request))
                         :response)
            body (when (:body response)
                   (json/read-str (:body response) :key-fn keyword))]
        
        (is (= 201 (:status response)))
        (is (string? (get-in response [:headers "Location"])))
        (is (= "test-mec-app" (:appName body)))
        (is (= "Acme Corp" (:appProvider body)))
        (is (= "1.0.0" (:appSoftVersion body)))
        (is (= "CREATED" (:onboardingState body)))
        (is (= "DISABLED" (:operationalState body)))
        (is (= "NOT_IN_USE" (:usageState body)))
        (is (map? (:_links body)))
        
        ;; Store package ID for subsequent tests
        (def app-pkg-id (:appPkgId body))
        (def created-module-id (:id body))
        
        (testing "GET /app_packages/{appPkgId} - Retrieve created package"
          (let [get-response (-> session-admin
                                 (request (str base-uri "/app_packages/" app-pkg-id)
                                          :request-method :get)
                                 :response)
                get-body (json/read-str (:body get-response) :key-fn keyword)]
            
            (is (= 200 (:status get-response)))
            (is (= app-pkg-id (:appPkgId get-body)))
            (is (= "test-mec-app" (:appName get-body)))
            (is (= "CREATED" (:onboardingState get-body)))))
        
        (testing "GET /app_packages - Query all packages"
          (let [query-response (-> session-admin
                                   (request (str base-uri "/app_packages")
                                            :request-method :get)
                                   :response)
                query-body (json/read-str (:body query-response) :key-fn keyword)
                packages (:AppPkgInfo query-body)]
            
            (is (= 200 (:status query-response)))
            (is (vector? packages))
            (is (pos? (count packages)))
            (is (some #(= app-pkg-id (:appPkgId %)) packages))))
        
        (testing "GET /app_packages?appName=test-mec-app - Filter by name"
          (let [query-response (-> session-admin
                                   (request (str base-uri "/app_packages?appName=test-mec-app")
                                            :request-method :get)
                                   :response)
                query-body (json/read-str (:body query-response) :key-fn keyword)
                packages (:AppPkgInfo query-body)]
            
            (is (= 200 (:status query-response)))
            (is (= 1 (count packages)))
            (is (= "test-mec-app" (:appName (first packages))))))
        
        (testing "GET /app_packages?appProvider=Acme Corp - Filter by provider"
          (let [query-response (-> session-admin
                                   (request (str base-uri "/app_packages?appProvider=Acme%20Corp")
                                            :request-method :get)
                                   :response)
                query-body (json/read-str (:body query-response) :key-fn keyword)
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
                                  :body (json/write-str invalid-request))
                         :response)
            body (when (:body response)
                   (json/read-str (:body response) :key-fn keyword))]
        
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
                   (json/read-str (:body response) :key-fn keyword))]
        
        (is (= 404 (:status response)))
        (is (= 404 (:status body)))
        (is (= "Not Found" (:title body)))))
    
    (testing "DELETE /app_packages/{nonexistent} - Not Found"
      (let [response (-> session-admin
                         (request (str base-uri "/app_packages/module/nonexistent-456")
                                  :request-method :delete)
                         :response)]
        
        (is (= 404 (:status response)))))))


(deftest mm1-mec-module-integration
  (let [session-admin (-> (ltu/ring-app)
                          session
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-anon"))]
    
    (testing "Create MEC module and verify it appears in Mm1 query"
      ;; Create a module-application-mec directly
      (let [mec-module {:name "Direct MEC Module"
                        :description "MEC module created directly"
                        :subtype "application_mec"
                        :path "test/mec-modules/direct"
                        :parent-path "test/mec-modules"
                        :published false
                        :content {:appName "Direct MEC App"
                                  :appDId (str "appd-direct-" (random-uuid))
                                  :appProvider "Test Provider"
                                  :appSoftVersion "2.0.0"
                                  :appDVersion "3.2.1"
                                  :mecVersion "2.2.1"
                                  :virtualComputeDescriptor [{:virtualComputeDescId "compute-1"
                                                              :virtualCpu {:numVirtualCpu 2}
                                                              :virtualMemory {:virtualMemSize 2048}}]
                                  :swImageDescriptor []
                                  :virtualStorageDescriptor []
                                  :appExtCpd []
                                  :appServiceRequired []
                                  :trafficRuleDescriptor []
                                  :dnsRuleDescriptor []
                                  :appFeatureRequired []}}
            create-response (-> session-admin
                                (request (str p/service-context "/api/module")
                                         :request-method :post
                                         :body (json/write-str mec-module))
                                :response)
            module-id (get-in create-response [:body :resource-id])]
        
        (is (= 201 (:status create-response)))
        (is (string? module-id))
        
        ;; Query via Mm1 API
        (let [query-response (-> session-admin
                                 (request (str base-uri "/app_packages?appName=Direct%20MEC%20App")
                                          :request-method :get)
                                 :response)
              query-body (json/read-str (:body query-response) :key-fn keyword)
              packages (:AppPkgInfo query-body)]
          
          (is (= 200 (:status query-response)))
          (is (some #(= "Direct MEC App" (:appName %)) packages)))
        
        ;; Cleanup
        (-> session-admin
            (request (str base-uri "/app_packages/" module-id)
                     :request-method :delete))))))
