(ns sixsq.nuvla.server.util.general-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.util.general :as t]))

(deftest test-date-from-str
  (are [expect arg] (= expect (t/filter-map-nil-value arg))
                    {} nil
                    {} {}
                    {:a 1} {:a 1}
                    {:a 1 :b "b"} {:a 1 :b "b"}
                    {:a 1 :c {} :d false} {:d false :a 1 :b nil :c {}}))
