(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-item-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration-test :as enumeration]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-item :as spec]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range-test :as range]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value-test :as single-value]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-unit-test :as unit]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:item {:alpha enumeration/valid
                   :beta  range/valid
                   :gamma single-value/valid
                   :delta unit/valid}})


(deftest check-value-scope-item

  (stu/is-valid ::spec/collection-item valid)

  (stu/is-invalid ::spec/collection-item (assoc valid :badAttribute 1)))
