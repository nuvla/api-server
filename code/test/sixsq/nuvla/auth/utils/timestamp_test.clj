(ns sixsq.nuvla.auth.utils.timestamp-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [sixsq.nuvla.auth.utils.timestamp :as t]))


(def rfc822-like-pattern #"\w\w\w, \d{1,2} \w\w\w \d\d\d\d \d\d:\d\d:\d\d GMT")


(def iso8601-like-pattern #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(([\+-]\d\d:\d\d)|Z)")


(deftest check-timestamp-formatting
  (is (not (str/blank? (t/expiry-later-rfc822))))
  (is (re-matches rfc822-like-pattern (t/expiry-later-rfc822)))
  (is (not (str/blank? (t/expiry-later-rfc822 10))))
  (is (re-matches rfc822-like-pattern (t/expiry-later-rfc822 10))))


(deftest check-expiry-now
  (is (not (str/blank? (t/expiry-now-rfc822))))
  (is (re-matches rfc822-like-pattern (t/expiry-now-rfc822))))


(deftest check-conversion
  (let [now-rfc822  (t/expiry-now-rfc822)
        now-iso8601 (t/rfc822->iso8601 now-rfc822)]
    (is (not (str/blank? now-iso8601)))
    (is (re-matches iso8601-like-pattern now-iso8601))))
