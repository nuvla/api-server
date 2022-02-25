(ns sixsq.nuvla.server.util.time-test
  (:require
    [clojure.test :refer [deftest are]]
    [sixsq.nuvla.server.util.time :refer [date-from-str]]))

(deftest test-date-from-str
  (are [expect arg] (= expect (some-> arg date-from-str .toString))
                    nil nil
                    nil 1
                    nil "wrong-value"
                    nil "1964-08-25T10:00:00.00Wrong"
                    nil "2022-02-25T08:05:15.8Z"
                    "2015-04-14T11:07:36.639Z" "2015-04-14T11:07:36.639Z"
                    "2022-02-25T08:40:18.224Z" "2022-02-25T08:40:18.224Z"
                    "1964-08-25T10:00Z" "1964-08-25T10:00:00.00Z"
                    "2000-08-25T10:00:00.010Z" "2000-08-25T10:00:00.01Z"
                    "1964-12-25T10:00+01:00" "1964-12-25T10:00:00.00+01:00"))
