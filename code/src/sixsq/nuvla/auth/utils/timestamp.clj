(ns sixsq.nuvla.auth.utils.timestamp
  "Utilities for creating expiration times for token claims and for formatting
   them correctly for the cookie 'expires' field."
  (:require
    [java-time :as t]
    [sixsq.nuvla.server.util.time :as time]))


(def default-ttl-minutes (* 24 60))                         ;; 1 day


(defn rfc822
  "Returns a timestamp formatted in cookie date format (RFC822) from the
   number of **seconds** since the epoch."
  [seconds-since-epoch]
  (t/with-clock
    (t/system-clock "UTC")
    (let [millis-since-epoch (* seconds-since-epoch 1000)
          offset-date-time (-> millis-since-epoch
                               (t/instant)
                               (t/offset-date-time (t/zone-id)))]
      (t/format time/rfc822-formatter offset-date-time))))


(defn expiry-later
  "Returns the expiry timestamp as the number of **seconds** since the epoch.
   If n is provided, then the expiry timestamp corresponds to n minutes later.
   If it is not provided, then the default lifetime is used."
  [& [n]]
  (-> (or n default-ttl-minutes)
      (time/from-now :minutes)
      (t/to-millis-from-epoch)
      (quot 1000)))


(defn expiry-later-rfc822
  [& [n]]
  (rfc822 (expiry-later n)))


(defn expiry-now-rfc822
  []
  (rfc822 (expiry-later 0)))


(defn rfc822->iso8601
  [rfc822]
  (->> rfc822
       (t/offset-date-time time/rfc822-formatter)
       (t/format time/iso8601-formatter)))

