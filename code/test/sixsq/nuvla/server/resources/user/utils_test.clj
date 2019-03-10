(ns sixsq.nuvla.server.resources.user.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.user.utils :as t])
  (:import
    (clojure.lang ExceptionInfo)))


(deftest check-password-constraints

  (let [password (apply str (repeat t/min-password-length "a"))
        password-mismatch (str password "-mismatch")
        password-short (apply str (repeat (dec t/min-password-length) "a"))
        valid {:password password, :passwordRepeat password}]

    (is (t/check-password-constraints valid))

    (are [msg-pattern resource] (thrown-with-msg? ExceptionInfo msg-pattern (t/check-password-constraints resource))
                                #"both" {}
                                #"both" (dissoc valid :password)
                                #"both" (dissoc valid :passwordRepeat)

                                #"identical" (assoc valid :password password-mismatch)
                                #"identical" (assoc valid :passwordRepeat password-mismatch)

                                #"characters" {:password password-short, :passwordRepeat password-short})))
