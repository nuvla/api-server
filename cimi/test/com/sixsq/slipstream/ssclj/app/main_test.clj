(ns com.sixsq.slipstream.ssclj.app.main-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.main :as t]))

(deftest check-parse-port
  (are [expected arg] (is (= expected (t/parse-port arg)))
                      nil nil
                      nil (System/getProperties)
                      nil "badport"
                      nil "-1"
                      nil "65536"
                      nil "1.3"
                      1 "1"
                      65535 "65535"))
