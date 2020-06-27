(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-coe-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-coe :as tpl-coe]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-coe :as spec-coe]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-service-template-create-coe-schema
  (doseq [coe-type ["swarm" "kubernetes"]]
    (let [timestamp "1964-08-25T10:00:00.00Z"
          cfg       {:id                    (str tpl/resource-type "/1-2-3-4-5")
                     :resource-type         tpl/resource-type
                     :created               timestamp
                     :updated               timestamp
                     :acl                   valid-acl

                     :method                tpl-coe/method
                     :subtype               coe-type

                     :management-credential "credential/my-cloud-credential"}]

      (stu/is-valid ::spec-coe/schema cfg)

      (doseq [attr #{:id :resource-type :created :updated :acl :method}]
        (stu/is-invalid ::spec-coe/schema (dissoc cfg attr))))))


























