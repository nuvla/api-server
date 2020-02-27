(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-swarm :as tpl-swarm]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm :as spec-swarm]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-service-template-create-swarm-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                 (str tpl/resource-type "/swarm")
                   :resource-type      tpl/resource-type
                   :created            timestamp
                   :updated            timestamp
                   :acl                valid-acl

                   :method             tpl-swarm/method
                   :subtype            tpl-swarm/method

                   :service-credential {:href "credential/my-cloud-credential"}}]

    (stu/is-valid ::spec-swarm/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :service-credential}]
      (stu/is-invalid ::spec-swarm/schema (dissoc cfg attr)))))


























