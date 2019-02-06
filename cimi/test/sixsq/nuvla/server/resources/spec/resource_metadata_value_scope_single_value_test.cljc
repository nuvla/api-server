(ns sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:value 2000
            :units "MHz"})


(deftest check-value-scope-unit

  (stu/is-valid ::spec/single-value valid)

  (doseq [k #{:units}]
    (stu/is-valid ::spec/single-value (dissoc valid k)))

  (doseq [k #{:value}]
    (stu/is-invalid ::spec/single-value (dissoc valid k)))

  (stu/is-invalid ::spec/single-value (assoc valid :badAttribute 1))
  (stu/is-invalid ::spec/single-value (assoc valid :units ""))
  (stu/is-invalid ::spec/single-value (assoc valid :value ["cannot" "be" "collection"])))
