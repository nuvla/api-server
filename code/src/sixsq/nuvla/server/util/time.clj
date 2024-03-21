(ns sixsq.nuvla.server.util.time
  (:require [java-time :as t])
  (:import (java.time Instant LocalTime OffsetDateTime ZoneOffset)
           (java.time.temporal ChronoUnit)))

(def rfc822-formatter (t/formatter :rfc-1123-date-time))

(def iso8601-format "uuuu-MM-dd'T'HH:mm:ss[.SSS]XXXXX")

(def iso8601-formatter (t/formatter iso8601-format
                                    {:resolver-style :strict}))

(def utc-clock (t/system-clock "UTC"))

(def plus t/plus)

(def minus t/minus)

(def java-date t/java-date)

(def period t/period)

(def before? t/before?)

(def after? t/after?)

(def duration t/duration)

(defn now
  []
  (t/with-clock utc-clock
                (t/offset-date-time)))

(defn duration-unit
  [n unit]
  (case unit
    :millis (t/millis n)
    :seconds (t/seconds n)
    :minutes (t/minutes n)
    :hours (t/hours n)
    :days (t/days n)
    :weeks (t/weeks n)
    :months (t/months n)
    :years (t/years n)))

(defn time-between
  [start end unit]
  (t/time-between start end unit))

(defn from-now
  [n unit]
  (t/plus (now) (duration-unit n unit)))

(defn ago
  [n unit]
  (t/minus (now) (duration-unit n unit)))

(defn to-str
  [^OffsetDateTime date]
  (t/format iso8601-formatter date))

(defn now-str
  []
  (to-str (now)))

(defn date-from-str
  [^String string]
  (when (string? string)
    (try
      (t/offset-date-time iso8601-formatter string)
      (catch Exception _
        nil))))

(defn date-from-unix-timestamp
  [^Long timestamp]
  (-> (Instant/ofEpochSecond timestamp)
      (t/offset-date-time (t/zone-id "UTC"))))

(defn unix-timestamp-from-date
  [^OffsetDateTime date]
  (.toEpochSecond date))

(defn time-between-date-now
  [^String start-date unit]
  (-> start-date
      date-from-str
      (t/time-between (now) unit)))

(defn unix-timestamp->str
  [^Long timestamp]
  (-> timestamp
      date-from-unix-timestamp
      to-str))

(defn truncated-to-minutes
  [^OffsetDateTime date]
  (.truncatedTo date ChronoUnit/MINUTES))

(defn truncated-to-days
  [^OffsetDateTime date]
  (.truncatedTo date ChronoUnit/DAYS))

(defn end-of-day-date
  []
  (-> (now)
      (.toInstant)
      (.atZone ZoneOffset/UTC)
      (.with LocalTime/MAX)
      (.toOffsetDateTime)))

(def year t/year)
