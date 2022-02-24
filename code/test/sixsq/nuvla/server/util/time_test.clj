(ns sixsq.nuvla.server.util.time-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.util.time :refer [date-from-str]]))

(deftest date-from-str-utc
  (is (not (nil? (date-from-str "1964-08-25T10:00:00.00Z")))))
