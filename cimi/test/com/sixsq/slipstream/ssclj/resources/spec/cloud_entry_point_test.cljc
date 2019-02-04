(ns com.sixsq.slipstream.ssclj.resources.spec.cloud-entry-point-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.resources.cloud-entry-point :refer :all]
    [com.sixsq.slipstream.ssclj.resources.spec.cloud-entry-point :as cep]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


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
