(ns sixsq.nuvla.server.resources.callback.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.callback.utils :as t]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.time :as time]))


(deftest check-executable?
  (let [future (time/to-str (time/from-now 2 :weeks))
        past (time/to-str (time/ago 2 :weeks))]
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
