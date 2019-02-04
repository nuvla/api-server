(ns com.sixsq.slipstream.ssclj.resources.callback.utils-test
  (:require
    [clj-time.core :refer [ago from-now weeks]]
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.callback.utils :as t]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]))


(deftest check-executable?
  (let [future (-> 2 weeks from-now u/unparse-timestamp-datetime)
        past (-> 2 weeks ago u/unparse-timestamp-datetime)]
    (are [expected arg] (= expected (t/executable? arg))
                        true {:state "WAITING", :expires future}
                        false {:state "WAITING", :expires past}
                        true {:state "WAITING"}
                        false {:state "FAILED", :expires future}
                        false {:state "FAILED", :expires past}
                        false {:state "FAILED"}
                        false {:state "SUCCEEDED", :expires future}
                        false {:state "SUCCEEDED", :expires past}
                        false {:state "SUCCEEDED"}
                        false {})))
