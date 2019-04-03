(ns sixsq.nuvla.server.resources.spec.resource-metadata-attribute-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.resource-metadata-attribute :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:name               "my-action"
            :type               "string"
            :provider-mandatory true
            :consumer-mandatory true
            :mutable            true
            :consumer-writable  true

            :display-name       "my action"
            :description        "a wonderful attribute"
            :help               "just give me a value"
            :group              "body"
            :category           "some string for a category"
            :order              10
            :hidden             false
            :sensitive          false
            :lines              3
            :indexed            true})


(deftest check-attribute

  ;; attribute

  (stu/is-valid ::spec/attribute valid)

  ;; mandatory attributes
  (doseq [k #{:name :type :provider-mandatory :consumer-mandatory :mutable :consumer-writable}]
    (stu/is-invalid ::spec/attribute (dissoc valid k)))

  ;; optional attributes
  (doseq [k #{:namespace :uri :display-name :description :help :group
              :category :order :hidden :sensitive :lines :indexed}]
    (stu/is-valid ::spec/attribute (dissoc valid k)))

  (stu/is-invalid ::spec/attribute (assoc valid :badAttribute 1))
  (stu/is-invalid ::spec/attribute (assoc valid :name " bad name "))
  (stu/is-invalid ::spec/attribute (assoc valid :type "unknown-type"))
  (stu/is-invalid ::spec/attribute (assoc valid :namespace ""))
  (stu/is-invalid ::spec/attribute (assoc valid :order "bad-value"))
  (stu/is-invalid ::spec/attribute (assoc valid :enum []))

  ;; attribute vector

  (stu/is-valid ::spec/attributes [valid])
  (stu/is-valid ::spec/attributes [valid valid])
  (stu/is-valid ::spec/attributes (list valid))
  (stu/is-invalid ::spec/attributes []))
