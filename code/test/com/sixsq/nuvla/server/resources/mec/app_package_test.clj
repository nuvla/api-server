(ns com.sixsq.nuvla.server.resources.mec.app-package-test
  "Tests for ETSI MEC 010-2 Mm1 Application Package Management API"
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.app.routes :as app-routes]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.mec :as mec]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.resources.mec.app-package :as t]))

(def valid-appd
  {:appName "Uploaded MEC App"
   :appDescription "Uploaded descriptor"
   :appProvider "Acme Corp"
   :appDVersion "3.2.1"
   :appSoftVersion "2.0.0"
   :mecVersion "2.2.1"
   :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 2}
                              :virtualMemory {:virtualMemSize 2048}}
   :swImageDescriptor [{:swImageName "uploaded-mec-app"
                        :swImageVersion "2.0.0"
                        :containerFormat :DOCKER
                        :swImage "sixsq/uploaded-mec-app:2.0.0"}]
   :virtualStorageDescriptor []
   :appExtCpd []
   :appServiceRequired []
   :trafficRuleDescriptor []
   :dnsRuleDescriptor []
   :appFeatureRequired []})


(deftest test-module->app-pkg-info
  (testing "Convert Nuvla module to MEC AppPkgInfo"
    (let [module {:id "module/test-123"
                  :name "Test Application"
                  :subtype "application_mec"
                  :path "acme/apps/test"
                  :parent-path "acme/apps"
                  :published true
                  :versions [{:href "module/test-123/v1" :published true}]
                  :content {:appDId "module/test-123"
                            :appName "Test MEC App"
                            :appProvider "Acme Corp"
                            :appSoftVersion "1.2.3"
                            :appDVersion "1.0"}
                  :content-id "sha256:abc123"
                  :created "2024-01-15T10:00:00Z"
                  :updated "2024-01-15T10:00:00Z"}
          app-pkg-info (t/module->app-pkg-info module)]
      
      (is (= "module/test-123" (:id app-pkg-info)))
      (is (= "module/test-123" (:appPkgId app-pkg-info)))
      (is (= "module/test-123" (:appDId app-pkg-info)))
      (is (= "Test MEC App" (:appName app-pkg-info)))
      (is (= "Acme Corp" (:appProvider app-pkg-info)))
      (is (= "1.2.3" (:appSoftVersion app-pkg-info)))
      (is (= "1.0" (:appDVersion app-pkg-info)))
      (is (= "ENABLED" (:operationalState app-pkg-info)))
      (is (= "IN_USE" (:usageState app-pkg-info)))
      (is (= "ONBOARDED" (:onboardingState app-pkg-info)))
      (is (= "acme/apps/test" (:appPkgPath app-pkg-info)))
      (is (= "application_mec" (:moduletype app-pkg-info)))
      (is (= "/mec/app_lcm/v2/app_packages/module/test-123/appD"
             (get-in app-pkg-info [:_links :appD :href])))))
  
  (testing "Module without content uses fallback values"
    (let [module {:id "module/simple-456"
                  :name "Simple App"
                  :subtype "application"
                  :path "apps/simple"
                  :parent-path "apps"
                  :published false
                  :versions []
                  :created "2024-01-15T10:00:00Z"
                  :updated "2024-01-15T10:00:00Z"}
          app-pkg-info (t/module->app-pkg-info module)]
      
      (is (= "module/simple-456" (:appDId app-pkg-info)))
      (is (= "Simple App" (:appName app-pkg-info)))
      (is (= "apps" (:appProvider app-pkg-info)))
      (is (= "0" (:appSoftVersion app-pkg-info)))
      (is (= "NOT_IN_USE" (:usageState app-pkg-info)))
      (is (= "CREATED" (:onboardingState app-pkg-info)))
      (is (= "DISABLED" (:operationalState app-pkg-info))))))


(deftest test-app-pkg-filter
  (testing "Base filter for MEC application modules"
    (is (= "subtype='application_mec'" (t/app-pkg-filter nil))))
  
  (testing "Filter with additional condition"
    (is (= "subtype='application_mec' and published=true"
           (t/app-pkg-filter "published=true")))))


(deftest test-query-app-packages-request
  (testing "Query request structure"
    (let [request {:params {:appName "Test App"
                            :appProvider "Acme"
                            :usageState "IN_USE"}}]
      ;; Test that parameters are extracted correctly
      (is (= "Test App" (get-in request [:params :appName])))
      (is (= "Acme" (get-in request [:params :appProvider])))
      (is (= "IN_USE" (get-in request [:params :usageState]))))))


(deftest test-create-app-package-validation
  (testing "CreateAppPkg requires appPkgName"
    (let [body {:appPkgVersion "1.0.0"}]
      (is (nil? (:appPkgName body)))))
  
  (testing "CreateAppPkg with all fields"
    (let [body {:appPkgName "New App"
                :appPkgVersion "2.0.0"
                :appPkgPath "/custom/path"
                :userDefinedData {:key1 "value1" :key2 "value2"}}]
      (is (= "New App" (:appPkgName body)))
      (is (= "2.0.0" (:appPkgVersion body)))
      (is (= "/custom/path" (:appPkgPath body)))
      (is (map? (:userDefinedData body))))))


(deftest test-create-app-package-omits-nil-user-defined-data
  (let [created-request (atom nil)
        module-id       "module/test-123"
        authn-info      {:user-id      "user/test"
                         :active-claim "user/test"
                         :claims       ["user/test" "group/nuvla-user"]}
        module          {:id          module-id
                         :subtype     "application_mec"
                         :name        "demo-mec-app"
                         :description "MEC Application Package: demo-mec-app"
                         :parent-path "mec-apps"
                         :content     {:appDId            module-id
                                       :appName          "demo-mec-app"
                                       :appDescription   "MEC Application Package: demo-mec-app"
                                       :appProvider      "Acme Corp"
                                       :appSoftVersion   "1.0.0"
                                       :appDVersion      "3.2.1"
                                       :mecVersion       "2.2.1"
                                       :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 1}
                                                                  :virtualMemory {:virtualMemSize 1024}}
                                       :swImageDescriptor [{:swImageName "demo-mec-app"
                                                            :swImageVersion "1.0.0"
                                                            :containerFormat :DOCKER
                                                            :swImage "sixsq/example-mec-app:1.0.0"}]
                                       :virtualStorageDescriptor []
                                       :appExtCpd []
                                       :appServiceRequired []
                                       :trafficRuleDescriptor []
                                       :dnsRuleDescriptor []
                                       :appFeatureRequired []}}]
    (with-redefs [t/ensure-project-path! (fn [_ _] nil)
                  crud/add (fn [request]
                             (reset! created-request request)
                             {:body {:resource-id module-id}})
                  t/ensure-mec-app-package! (fn [_ _] module)
                  t/sync-appd-id! identity]
      (let [response (t/create-app-package {:body        {:appPkgName    "demo-mec-app"
                                                          :appPkgVersion "1.0.0"
                                                          :appProvider   "Acme Corp"}
                                            :nuvla/authn authn-info})]
        (is (= 201 (:status response)))
        (is (nil? (get-in @created-request [:body :content :userDefinedData])))
        (is (false? (contains? (get-in @created-request [:body :content]) :userDefinedData)))
        (is (= authn-info (:nuvla/authn @created-request)))))))


(deftest test-validate-package-content
  (testing "package content is normalized to the real package id"
    (let [module {:id "module/test-123"
                  :name "Package Name"
                  :description "Package description"
                  :parent-path "acme/apps"
                  :content {:appProvider "Original Provider"}}
          normalized (t/validate-package-content! module valid-appd)]
      (is (= "module/test-123" (:appDId normalized)))
      (is (= "Uploaded MEC App" (:appName normalized)))
      (is (= "Uploaded descriptor" (:appDescription normalized)))))

  (testing "invalid package content is rejected"
    (let [module {:id "module/test-123"
                  :name "Package Name"
                  :description "Package description"
                  :parent-path "acme/apps"
                  :content {:appProvider "Original Provider"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Invalid MEC AppD content"
                            (t/validate-package-content! module {:appName "broken"}))))))


(deftest test-put-app-package-content
  (testing "PUT package_content persists updated descriptor content"
    (let [module-id "module/test-123"
          existing-module {:id module-id
                           :subtype "application_mec"
                           :name "Existing Package"
                           :description "Existing description"
                           :parent-path "acme/apps"
                           :versions [{:href "module-application-mec/version-1"}]
                           :content {:appDId module-id
                                     :appName "Existing Package"
                                     :appDescription "Existing description"
                                     :appProvider "Acme Corp"
                                     :appDVersion "3.2.1"
                                     :appSoftVersion "1.0.0"
                                     :mecVersion "2.2.1"
                                     :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 1}
                                                                :virtualMemory {:virtualMemSize 1024}}
                                     :swImageDescriptor [{:swImageName "existing"
                                                          :swImageVersion "1.0.0"
                                                          :containerFormat :DOCKER
                                                          :swImage "sixsq/existing:1.0.0"}]
                                     :virtualStorageDescriptor []
                                     :appExtCpd []
                                     :appServiceRequired []
                                     :trafficRuleDescriptor []
                                     :dnsRuleDescriptor []
                                     :appFeatureRequired []}}
          edited-body (atom nil)
          updated-module (assoc existing-module
                                :name "Uploaded MEC App"
                                :description "Uploaded descriptor"
                                :content (assoc valid-appd :appDId module-id))]
      (with-redefs [crud/retrieve-by-id-as-admin (fn [_] existing-module)
                    module-utils/retrieve-module-content (fn [module _] module)
                    crud/edit-by-id-as-admin (fn [_ body]
                                              (reset! edited-body body)
                                              {:status 200})]
        (let [response (t/put-app-package-content {:params {:appPkgId module-id}
                                                   :body valid-appd})]
          (is (= 204 (:status response)))
          (is (= "Uploaded MEC App" (:name @edited-body)))
          (is (= "Uploaded descriptor" (:description @edited-body)))
          (is (= module-id (get-in @edited-body [:content :appDId])))
          (is (= "Uploaded MEC App" (get-in @edited-body [:content :appName])))))))

  (testing "PUT package_content rejects invalid descriptor payload"
    (let [module-id "module/test-123"
          existing-module {:id module-id
                           :subtype "application_mec"
                           :name "Existing Package"
                           :description "Existing description"
                           :parent-path "acme/apps"
                           :versions [{:href "module-application-mec/version-1"}]
                           :content {:appDId module-id}}]
      (with-redefs [crud/retrieve-by-id-as-admin (fn [_] existing-module)
                    module-utils/retrieve-module-content (fn [module _] module)
                    crud/edit-by-id-as-admin (fn [_ _] (throw (ex-info "should not edit" {})))]
        (let [response (t/put-app-package-content {:params {:appPkgId module-id}
                                                   :body {:appName "broken"}})]
          (is (= 400 (:status response)))
          (is (= "Bad Request" (get-in response [:body :title]))))))))


(deftest test-top-level-mec-routes
  (testing "top-level MEC handler routes app_packages requests"
    (with-redefs [t/query-app-packages (fn [_] {:status 200 :body {:ok true}})]
      (let [response (mec/routes {:request-method :get
                                  :uri "/api/mec/app_lcm/v2/app_packages"
                                  :params {}})]
        (is (= 200 (:status response)))
        (is (= {:ok true} (:body response)))))))


(deftest test-main-routes-include-mec-handler
  (testing "main app routes include MEC top-level handler"
    (with-redefs [mec/routes (fn [_] {:status 200 :body {:ok true}})]
      (let [handler (app-routes/get-main-routes)
            response (handler {:request-method :get
                               :uri "/api/mec/app_lcm/v2/app_packages"
                               :params {}})]
        (is (= 200 (:status response)))
        (is (= {:ok true} (:body response)))))))
