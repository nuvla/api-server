(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic :as spec-generic]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-service-template-generic-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg {:id            (str infra-service-tpl/resource-type "/generic")
             :resource-type infra-service-tpl/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :method        infra-service-tpl-generic/method

             :subtype          "s3"
             :endpoint      "https://s3.example.org:2000"
             :state         "STARTED"}]

    (stu/is-valid ::spec-generic/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :subtype :endpoint}]
      (stu/is-invalid ::spec-generic/schema (dissoc cfg attr)))

    (doseq [attr #{:state}]
      (stu/is-valid ::spec-generic/schema (dissoc cfg attr)))))


























