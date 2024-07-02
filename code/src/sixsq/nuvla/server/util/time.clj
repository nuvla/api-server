(ns sixsq.nuvla.server.util.time
  (:require [java-time.api :as t])
  (:import (java.time Clock Instant LocalTime OffsetDateTime ZoneOffset)
           (java.time.format DateTimeFormatter DateTimeFormatterBuilder ResolverStyle SignStyle)
           (java.time.temporal ChronoUnit)
           (java.time.temporal ChronoField ChronoUnit)
           (java.util Date)))

(def rfc822-formatter DateTimeFormatter/RFC_1123_DATE_TIME)


(def iso8601-formatter-base
  (-> (doto
        (DateTimeFormatterBuilder.)
        (.appendValue ChronoField/YEAR, 4, 4, SignStyle/NOT_NEGATIVE)
        (.appendLiteral "-")
        (.appendValue ChronoField/MONTH_OF_YEAR, 2, 2, SignStyle/NOT_NEGATIVE)
        (.appendLiteral "-")
        (.appendValue ChronoField/DAY_OF_MONTH, 2, 2, SignStyle/NOT_NEGATIVE)
        (.optionalStart)
        (.appendLiteral "T")
        (.optionalStart)
        (.appendValue ChronoField/HOUR_OF_DAY, 2, 2, SignStyle/NOT_NEGATIVE)
        (.optionalStart)
        (.appendLiteral ":")
        (.appendValue ChronoField/MINUTE_OF_HOUR, 2, 2, SignStyle/NOT_NEGATIVE)
        (.optionalStart)
        (.appendLiteral ":")
        (.appendValue ChronoField/SECOND_OF_MINUTE, 2, 2, SignStyle/NOT_NEGATIVE))
      (.toFormatter)))

(def iso8601-formatter-input
  (-> (doto
        (DateTimeFormatterBuilder.)
        (.append iso8601-formatter-base)
        (.optionalStart)
        (.appendFraction ChronoField/MILLI_OF_SECOND, 0, 3, true)
        (.optionalEnd)
        (.appendZoneOrOffsetId))
      (.toFormatter)
      (.withResolverStyle ResolverStyle/STRICT)))

(def iso8601-formatter-output
  (-> (doto
        (DateTimeFormatterBuilder.)
        (.append iso8601-formatter-base)
        (.appendFraction ChronoField/MILLI_OF_SECOND, 3, 3, true)
        (.appendZoneOrOffsetId))
      (.toFormatter)))

(def utc-clock (Clock/systemUTC))

(def plus t/plus)

(def minus t/minus)

(defn java-date
  [^OffsetDateTime date]
  (Date/from (.toInstant date)))

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
  (.format iso8601-formatter-output date))

(defn now-str
  []
  (to-str (now)))

(defn parse-date-fmt
  [^String s ^DateTimeFormatter fmt]
  (try
    (OffsetDateTime/from (.parse fmt s))
    (catch Exception _)))

(defn parse-date
  [^String s]
  (try
    (parse-date-fmt s iso8601-formatter-input)
    (catch Exception _)))

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
      parse-date
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

(def min-time t/min)
(def max-time t/max)
