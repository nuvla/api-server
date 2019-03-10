(ns sixsq.nuvla.server.resources.spec.resource-metadata-attribute-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.resource-metadata-attribute :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:name              "my-action"
            :type              "string"
            :providerMandatory true
            :consumerMandatory true
            :mutable           true
            :consumerWritable  true

            :namespace         "https://sixsq.com/slipstream/"
            :uri               "https://sixsq.com/slipstream/param-info"
            :displayName       "my action"
            :description       "a wonderful attribute"
            :help              "just give me a value"
            :group             "body"
            :category          "some string for a category"
            :order             10
            :hidden            false
            :sensitive         false
            :lines             3})


(deftest check-attribute

  ;; attribute

  (stu/is-valid ::spec/attribute valid)

  (doseq [k #{:namespace :uri :displayName :description :help :group :category :order :hidden :sensitive :lines}]
    (stu/is-valid ::spec/attribute (dissoc valid k)))

  (doseq [k #{:name :type :providerMandatory :consumerMandatory :mutable :consumerWritable}]
    (stu/is-invalid ::spec/attribute (dissoc valid k)))

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
