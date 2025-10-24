(ns com.sixsq.nuvla.server.resources.module-application-mec-test
  "Tests for MEC 037 Application Descriptor module subtype"
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.module-application-mec :as t]
    [com.sixsq.nuvla.server.resources.spec.module-application-mec :as spec-mec]
    [clojure.spec.alpha :as s]))


(deftest check-subtype-constant
  (testing "Subtype constant matches spec"
    (is (= "application_mec" t/subtype))
    (is (= "application_mec" spec-mec/subtype))))


(deftest check-resource-type
  (testing "Resource type is correctly formed"
    (is (= "module-application-mec" t/resource-type))))


(deftest test-validate-appd-content
  (testing "Valid MEC AppD content passes validation"
    (let [valid-appd {:appDId "module/test-app-123"
                      :appDVersion "1.0"
                      :appName "Test MEC App"
                      :appProvider "Test Provider"
                      :appSoftVersion "1.0.0"
                      :mecVersion "3.1.1"
                      :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 2}
                                                  :virtualMemory {:virtualMemSize 2048}}
                      :swImageDescriptor [{:swImageName "test-image"
                                           :swImageVersion "1.0"
                                           :containerFormat :DOCKER
                                           :swImage "registry.example.com/test:1.0"
                                           :minDisk 1
                                           :minRam 512}]}]
      (is (= valid-appd (t/validate-appd-content valid-appd)))))

  (testing "Invalid MEC AppD content throws exception"
    (let [invalid-appd {:appName "Test" ;; Missing required fields
                        :appProvider "Provider"}]
      (is (thrown? Exception (t/validate-appd-content invalid-appd))))))


(deftest test-validate-container-images
  (testing "Valid container image passes validation"
    (let [content {:swImageDescriptor [{:swImageName "test"
                                        :swImageVersion "1.0"
                                        :containerFormat :DOCKER
                                        :swImage "registry.example.com/app:1.0"
                                        :minDisk 1
                                        :minRam 256}]}]
      (is (= content (t/validate-container-images content)))))

  (testing "Missing images throws exception"
    (let [content {:swImageDescriptor []}]
      (is (thrown? Exception (t/validate-container-images content)))))

  (testing "Invalid image format throws exception"
    (let [content {:swImageDescriptor [{:swImageName "test"
                                        :swImageVersion "1.0"
                                        :containerFormat :DOCKER
                                        :swImage "INVALID FORMAT!"
                                        :minDisk 1
                                        :minRam 256}]}]
      (is (thrown? Exception (t/validate-container-images content))))))


(deftest test-extract-resource-summary
  (testing "Resource summary extraction"
    (let [content {:mecVersion "3.1.1"
                   :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 4}
                                              :virtualMemory {:virtualMemSize 8192}}
                   :virtualStorageDescriptor [{:sizeOfStorage 100}
                                              {:sizeOfStorage 50}]
                   :swImageDescriptor [{:swImageName "test1"}
                                       {:swImageName "test2"}]
                   :appServiceRequired [{:serName :rnis}
                                        {:serName :location}]}
          summary (t/extract-resource-summary content)]
      (is (= 4 (:cpus summary)))
      (is (= 8192 (:memory-mb summary)))
      (is (= 150 (:storage-gb summary)))
      (is (= 2 (:images summary)))
      (is (= "3.1.1" (:mec-version summary)))
      (is (= [:rnis :location] (:requires-mec-services summary))))))


(deftest test-extract-deployment-info
  (testing "Deployment info extraction"
    (let [content {:appName "My App"
                   :appSoftVersion "1.2.3"
                   :appProvider "Acme Corp"
                   :appDescription "Test application"
                   :mecVersion "3.1.1"
                   :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 2}
                                              :virtualMemory {:virtualMemSize 4096}}
                   :virtualStorageDescriptor [{:sizeOfStorage 50}]
                   :swImageDescriptor [{:swImageName "app"
                                        :swImageVersion "1.2.3"
                                        :swImage "registry.io/app:1.2.3"
                                        :containerFormat :DOCKER}]
                   :appExtCpd [{:cpdId "eth0"}]
                   :trafficRuleDescriptor [{:trafficRuleId "rule1"}]
                   :dnsRuleDescriptor [{:dnsRuleId "dns1"}]
                   :appServiceRequired [{:serName :rnis :version "2.1.1"}]}
          info (t/extract-deployment-info content)]
      (is (= "My App" (:app-name info)))
      (is (= "1.2.3" (:app-version info)))
      (is (= "Acme Corp" (:provider info)))
      (is (= 1 (count (:container-images info))))
      (is (= 2 (get-in info [:resource-requirements :cpus])))
      (is (= 1 (get-in info [:network-requirements :external-connections])))
      (is (= 1 (count (:mec-services info)))))))


(deftest test-appd-to-deployment-params
  (testing "AppD to deployment params conversion"
    (let [content {:appName "Test App"
                   :appProvider "Provider"
                   :appSoftVersion "1.0"
                   :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 2}}
                   :swImageDescriptor [{:swImage "registry.io/test:1.0"
                                        :containerFormat :DOCKER}]
                   :appServiceRequired [{:serName :rnis :version "2.1.1"}]
                   :trafficRuleDescriptor []
                   :dnsRuleDescriptor []}
          params (t/appd->deployment-params "module/test-123" content)]
      (is (= "module/test-123" (:appDId params)))
      (is (= "Test App" (:appName params)))
      (is (= "registry.io/test:1.0" (:containerImage params)))
      (is (= "DOCKER" (:containerFormat params)))
      (is (= 1 (count (:appServiceRequired params)))))))


(deftest test-check-mec-compatibility
  (testing "Compatible MEC AppD"
    (let [content {:mecVersion "3.1.1"
                   :appServiceRequired [{:serName :rnis}
                                        {:serName :location}]}
          capabilities {:mecVersion "3.2.1"
                        :availableServices [:rnis :location :bandwidth-management]}
          result (t/check-mec-compatibility content capabilities)]
      (is (:compatible? result))
      (is (:version-match? result))
      (is (:services-match? result))
      (is (empty? (:missing-services result)))))

  (testing "Incompatible - missing services"
    (let [content {:mecVersion "3.1.1"
                   :appServiceRequired [{:serName :rnis}
                                        {:serName :ue-identity}]}
          capabilities {:mecVersion "3.2.1"
                        :availableServices [:rnis :location]}
          result (t/check-mec-compatibility content capabilities)]
      (is (not (:compatible? result)))
      (is (:version-match? result))
      (is (not (:services-match? result)))
      (is (= #{:ue-identity} (:missing-services result)))))

  (testing "Incompatible - old MEC version"
    (let [content {:mecVersion "4.0.0"
                   :appServiceRequired [{:serName :rnis}]}
          capabilities {:mecVersion "3.2.1"
                        :availableServices [:rnis :location]}
          result (t/check-mec-compatibility content capabilities)]
      (is (not (:compatible? result)))
      (is (not (:version-match? result))))))
