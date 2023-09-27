(ns sixsq.nuvla.server.util.time-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.util.time :as t]))

(deftest test-date-from-str
  (are [expect arg] (= expect (some-> arg t/date-from-str .toString))
                    nil nil
                    nil 1
                    nil "wrong-value"
                    nil "1964-08-25T10:00:00.00Wrong"
                    nil "2022-02-25T08:05:15.8Z"
                    nil "2022-02-25"
                    nil "2001-02-29T10:00:00.01Z"           ; case date 29/2 not possible
                    "2015-04-14T11:07:36Z" "2015-04-14T11:07:36Z"
                    "2015-04-14T11:07:36.639Z" "2015-04-14T11:07:36.639Z"
                    "2022-02-25T08:40:18.224Z" "2022-02-25T08:40:18.224Z"
                    "1964-08-25T10:00Z" "1964-08-25T10:00:00.00Z"
                    "2000-08-25T10:00:00.010Z" "2000-08-25T10:00:00.01Z"
                    "1964-12-25T10:00+01:00" "1964-12-25T10:00:00.00+01:00"))

(deftest truncated-to-days-test
  (are [expect arg] (= expect (-> arg t/date-from-str
                                  t/truncated-to-days .toString))
                    "2015-04-14T00:00Z" "2015-04-14T11:07:36Z"
                    "2022-02-25T00:00Z" "2022-02-25T08:40:18.224Z"
                    "1964-08-25T00:00Z" "1964-08-25T00:00:00.00Z"))

(deftest unix-timestamp-from-date
  (is (= 1645778418
         (t/unix-timestamp-from-date
           (t/date-from-str "2022-02-25T08:40:18.224Z")))))

(deftest end-of-day-date
  (is (str/ends-with? (t/to-str (t/end-of-day-date)) "T23:59:59.999Z")))
