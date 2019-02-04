(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope-unit-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope-unit :as spec]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def valid {:units "MHz"})


(deftest check-value-scope-unit

  (stu/is-valid ::spec/unit valid)

  (doseq [k #{:units}]
    (stu/is-invalid ::spec/unit (dissoc valid k)))

  (stu/is-invalid ::spec/unit (assoc valid :badAttribute 1))
  (stu/is-invalid ::spec/unit (assoc valid :units "")))
