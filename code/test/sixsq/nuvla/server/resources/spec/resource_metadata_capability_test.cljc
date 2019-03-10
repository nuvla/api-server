(ns sixsq.nuvla.server.resources.spec.resource-metadata-capability-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.resource-metadata-capability :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:name        "my-action"
            :uri         "https://sixsq.com/slipstream/my-action"
            :description "a wonderful capability"
            :value       47})


(deftest check-capability

  ;; capability

  (stu/is-valid ::spec/capability valid)

  (doseq [k #{:name :description}]
    (stu/is-valid ::spec/capability (dissoc valid k)))

  (doseq [k #{:uri :value}]
    (stu/is-invalid ::spec/capability (dissoc valid k)))

  (stu/is-invalid ::spec/capability (assoc valid :badCapability 1))
  (stu/is-invalid ::spec/capability (assoc valid :name " bad name "))
  (stu/is-invalid ::spec/capability (assoc valid :uri ""))

  ;; capability vector

  (stu/is-valid ::spec/capabilities [valid])
  (stu/is-valid ::spec/capabilities [valid valid])
  (stu/is-valid ::spec/capabilities (list valid))
  (stu/is-invalid ::spec/capabilities []))
