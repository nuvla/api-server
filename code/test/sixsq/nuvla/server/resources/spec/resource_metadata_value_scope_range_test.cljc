(ns sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:minimum   10
            :maximum   100
            :increment 10
            :default   60
            :units     "KiB"})

(deftest check-value-scope-unit

  (stu/is-valid ::spec/range valid)

  (doseq [k #{:minimum :maximum :increment :default :units}]
    (stu/is-valid ::spec/range (dissoc valid k)))

  (stu/is-invalid ::spec/range (dissoc valid :minimum :maximum))

  (stu/is-invalid ::spec/range (assoc valid :badAttribute 1))
  (stu/is-invalid ::spec/range (assoc valid :default "bad value")))
