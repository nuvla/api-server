(ns sixsq.nuvla.server.resources.nuvlabox.utils-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as t]))


(deftest check-short-nb-id

  (is (nil? (t/short-nb-id nil)))
  (is (= "abc" (t/short-nb-id "nuvlabox/abc-def-ghi-jkl"))))
