(ns com.sixsq.nuvla.server.resources.spec.common-operation-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.common-operation :as common-operation]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-operation
  (stu/is-valid ::common-operation/operation {:href "uri" :rel "add"})
  (stu/is-invalid ::common-operation/operation {:href "uri"})
  (stu/is-invalid ::common-operation/operation {:rel "add"})
  (stu/is-invalid ::common-operation/operation {}))


(deftest check-operations
  (stu/is-valid ::common-operation/operations [{:href "uri" :rel "add"}])
  (stu/is-valid ::common-operation/operations [{:href "uri" :rel "add"} {:href "uri" :rel "delete"}])
  (stu/is-valid ::common-operation/operations []))
