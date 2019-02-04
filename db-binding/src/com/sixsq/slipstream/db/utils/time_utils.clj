(ns com.sixsq.slipstream.db.utils.time-utils
  (:require
    [clj-time.format :as time-fmt]))


(defn to-time-or-date
  [s]
  (try
    (time-fmt/parse (:date-time time-fmt/formatters) s)
    (catch IllegalArgumentException _
      (time-fmt/parse (:date time-fmt/formatters) s))))
