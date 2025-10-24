(ns com.sixsq.nuvla.server.resources.mec.app-package-test
  "Tests for ETSI MEC 010-2 Mm1 Application Package Management API"
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.mec.app-package :as t]))


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
      (is (= "application_mec" (:moduletype app-pkg-info)))))
  
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
      (is (= "NOT_IN_USE" (:usageState app-pkg-info))))))


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
