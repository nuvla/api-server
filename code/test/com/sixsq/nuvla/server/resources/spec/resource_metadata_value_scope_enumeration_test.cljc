(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration :as spec]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:values  ["68000" "Alpha" "ARM" "PA_RISC"]
            :default "Alpha"})


(deftest check-value-scope-unit

  (stu/is-valid ::spec/enumeration valid)

  (doseq [k #{:default}]
    (stu/is-valid ::spec/enumeration (dissoc valid k)))

  (doseq [k #{:values}]
    (stu/is-invalid ::spec/enumeration (dissoc valid k)))

  (stu/is-invalid ::spec/enumeration (assoc valid :badAttribute 1))
  (stu/is-invalid ::spec/enumeration (assoc valid :default ["cannot" "be" "collection"]))
  (stu/is-invalid ::spec/enumeration (assoc valid :values [])))
