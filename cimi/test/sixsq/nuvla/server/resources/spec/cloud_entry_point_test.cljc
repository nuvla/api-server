(ns sixsq.nuvla.server.resources.spec.cloud-entry-point-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.cloud-entry-point :refer :all]
    [sixsq.nuvla.server.resources.spec.cloud-entry-point :as cep]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-root-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id          resource-url
              :resourceURI p/service-context
              :created     timestamp
              :updated     timestamp
              :acl         resource-acl
              :baseURI     "http://cloud.example.org/"}]

    (stu/is-valid ::cep/cloud-entry-point root)
    (stu/is-valid ::cep/cloud-entry-point (assoc root :resources {:href "resource/uuid"}))

    (doseq [attr #{:id :resourceURI :created :updated :acl :baseURI}]
      (stu/is-invalid ::cep/cloud-entry-point (dissoc root attr)))))
