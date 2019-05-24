(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-kubernetes :as tpl-kubernetes]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-kubernetes :as spec-kubernetes]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-service-template-create-swarm-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                 (str tpl/resource-type "/kubernetes")
                   :resource-type      tpl/resource-type
                   :created            timestamp
                   :updated            timestamp
                   :acl                valid-acl

                   :method             tpl-kubernetes/method
                   :subtype            tpl-kubernetes/method

                   :service-credential {:href "credential/my-cloud-credential"}}]

    (stu/is-valid ::spec-kubernetes/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :service-credential}]
      (stu/is-invalid ::spec-kubernetes/schema (dissoc cfg attr)))))


























