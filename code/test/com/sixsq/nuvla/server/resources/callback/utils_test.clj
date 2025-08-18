(ns com.sixsq.nuvla.server.resources.callback.utils-test
  (:require
    [clojure.test :refer [are deftest]]
    [com.sixsq.nuvla.server.resources.callback.utils :as t]
    [com.sixsq.nuvla.server.util.time :as time]))


(deftest check-executable?
  (let [future (time/to-str (time/from-now 2 :weeks))
        past   (time/to-str (time/ago 2 :weeks))]
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

(deftest callback-ui-url
  (are [expected arg] (= expected (t/callback-ui-url arg))
                      "https://nuid.localhost/ui/callback?callback-url=https%3A%2F%2Fnuid.localhost%2Fapi%2Fcallback%2Fa1f08e95-7caf-4f84-8b62-dfa621eaad34%2Fexecute" "https://nuid.localhost/api/callback/a1f08e95-7caf-4f84-8b62-dfa621eaad34/execute"
                      "https://nuvla.io/ui/callback?callback-url=https%3A%2F%2Fnuvla.io%2Fapi%2Fcallback%2Fa1f08e95-7caf-4f84-8b62-dfa621eaad34%2Fexecute" "https://nuvla.io/api/callback/a1f08e95-7caf-4f84-8b62-dfa621eaad34/execute"))
