(ns sixsq.nuvla.auth.utils.timestamp
  "Utilities for creating expiration times for token claims and for formatting
   them correctly for the cookie 'expires' field."
  (:require
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [clj-time.format :as f]))


(def default-ttl-minutes (* 24 60))                  ;; 1 day


(def rfc822-formatter (:rfc822 f/formatters))


(def iso8601-formatter (:date-time f/formatters))


(defn rfc822
  "Returns a timestamp formatted in cookie date format (RFC822) from the
   number of **seconds** since the epoch."
  [seconds-since-epoch]
  (f/unparse rfc822-formatter (c/from-long (* 1000 seconds-since-epoch))))


(defn expiry-later
  "Returns the expiry timestamp as the number of **seconds** since the epoch.
   If n is provided, then the expiry timestamp corresponds to n minutes later.
   If it is not provided, then the default lifetime is used."
  [& [n]]
  (-> (or n default-ttl-minutes) t/minutes t/from-now c/to-long (quot 1000)))


(defn expiry-later-rfc822
  [& [n]]
  (rfc822 (expiry-later n)))


(defn expiry-now
  "Returns the current instant as the number of **seconds** since the epoch."
  []
  (-> (t/now) c/to-long (quot 1000)))


(defn expiry-now-rfc822
  []
  (rfc822 (expiry-now)))


(defn rfc822->iso8601
  [rfc822]
  (f/unparse iso8601-formatter (f/parse rfc822-formatter rfc822)))
