(ns sixsq.nuvla.server.resources.spec.acl-common-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.acl-common :as acl-common]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-principal

  (doseq [principal ["a/a" "a/1" "a-b/c-4"]]
    (stu/is-valid ::acl-common/principal principal))

  (doseq [principal ["" 0 true "/" "a/" "/a"
                     "a1/bad" "a/BAD" "a/b_a_d"]]
    (stu/is-invalid ::acl-common/principal principal)))


(deftest check-principals
  (doseq [principals [[]
                      ["a/a"]
                      ["a/a" "b/b"]]]
    (stu/is-valid ::acl-common/principals principals))

  (doseq [principals [nil
                      ["a/a" "a/a"]
                      ["a/a" 0]]]
    (stu/is-invalid ::acl-common/principals principals)))

