(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope :as spec]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration-test :as enumeration]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-item-test :as item]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range-test :as range]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value-test :as single-value]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-unit-test :as unit]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:alpha enumeration/valid
            :beta  range/valid
            :gamma single-value/valid
            :delta unit/valid
            :zeta  item/valid})


(def ^:const valid-value-scopes #{enumeration/valid
                                  range/valid
                                  single-value/valid
                                  unit/valid
                                  item/valid})


(deftest check-value-scope

  (doseq [value-scope valid-value-scopes]
    (stu/is-valid ::spec/value-scope value-scope)))
