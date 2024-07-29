(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-attribute-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-attribute :as spec]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration-test :as enumeration]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-item-test :as item]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range-test :as range]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value-test :as single-value]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-unit-test :as unit]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def ^:const valid {:name           "my-action"
                    :type           "string"
                    :server-managed true
                    :editable       true
                    :required       ["dummy"]               ;; really only meaningful for maps

                    :display-name   "my action"
                    :description    "a wonderful attribute"
                    :section        "data"
                    :order          10
                    :hidden         false
                    :sensitive      false
                    :indexed        true
                    :fulltext       true})


(def ^:const valid-value-scopes [{:name        "enumeration"
                                  :value-scope enumeration/valid}
                                 {:name        "range"
                                  :value-scope range/valid}
                                 {:name        "single-value"
                                  :value-scope single-value/valid}
                                 {:name        "unit"
                                  :value-scope unit/valid}
                                 {:name        "item"
                                  :value-scope item/valid}])


(def ^:const valid-attributes (doall (mapv (partial merge valid) valid-value-scopes)))


(def ^:const nested-attribute (assoc valid :type "map"
                                           :child-types [(assoc valid :value-scope unit/valid)
                                                         (assoc valid :value-scope single-value/valid)]
                                           :value-scope enumeration/valid))


(deftest check-attribute

  (doseq [attribute valid-attributes]

    ;; attribute

    (stu/is-valid ::spec/attribute attribute)

    ;; mandatory attributes
    (doseq [k #{:name :type}]
      (stu/is-invalid ::spec/attribute (dissoc attribute k)))

    ;; optional attributes
    (doseq [k #{:server-managed :required :editable
                :display-name :description :section
                :order :hidden :sensitive :indexed :fulltext}]
      (stu/is-valid ::spec/attribute (dissoc attribute k)))

    (stu/is-invalid ::spec/attribute (assoc attribute :badAttribute 1))
    (stu/is-invalid ::spec/attribute (assoc attribute :name " bad name "))
    (stu/is-invalid ::spec/attribute (assoc attribute :type "unknown-type"))
    (stu/is-invalid ::spec/attribute (assoc attribute :order "bad-value"))
    (stu/is-invalid ::spec/attribute (assoc attribute :enum []))

    ;; attribute vector

    (stu/is-valid ::spec/attributes [attribute])
    (stu/is-valid ::spec/attributes [attribute attribute])
    (stu/is-valid ::spec/attributes (list attribute))
    (stu/is-invalid ::spec/attributes []))

  ;; nested attribute
  (stu/is-valid ::spec/attribute nested-attribute)
  (stu/is-invalid ::spec/attribute (update-in nested-attribute [:child-types] conj (assoc valid :BAD_VALUE "NOT OK"))))
