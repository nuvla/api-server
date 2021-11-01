(ns sixsq.nuvla.server.resources.nuvlabox.utils-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as t]))


(deftest check-short-nb-id
  (is (nil? (t/short-nb-id nil)))
  (is (= "abc" (t/short-nb-id "nuvlabox/abc-def-ghi-jkl"))))


(deftest check-has-pull-support?
  (is (false? (t/has-pull-support? {:capabilities []})))
  (is (false? (t/has-pull-support? {})))
  (is (false? (t/has-pull-support? {:capabilities ["ANYTHING"]})))
  (is (t/has-pull-support? {:capabilities ["ANYTHING" "NUVLA_JOB_PULL"]})))
