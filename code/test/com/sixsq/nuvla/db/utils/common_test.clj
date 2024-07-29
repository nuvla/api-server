(ns com.sixsq.nuvla.db.utils.common-test
  (:require
    [clojure.test :refer [are deftest]]
    [com.sixsq.nuvla.db.utils.common :as t]))


(deftest check-split-id
  (are [expected id] (= expected (t/split-id id))
                     nil nil
                     ["cloud-entry-point" "cloud-entry-point"] "cloud-entry-point"
                     ["cloud-entry-point" "cloud-entry-point"] "cloud-entry-point/"
                     ["alpha-beta" "one-two"] "alpha-beta/one-two"))
