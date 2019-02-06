(ns sixsq.nuvla.auth.token-checker-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [sixsq.nuvla.auth.env-fixture :as env-fixture]
    [sixsq.nuvla.auth.token-checker :as t]
    [environ.core :as environ])
  (:import (java.util Properties)))

(deftest check-create-token
  (with-redefs [environ/env env-fixture/env-map]
    (is (t/create-token {:alpha 1, :beta true, :gamma "OK"}))
    (is (nil? (t/create-token nil)))))

(deftest check-valid-token?
  (with-redefs [environ/env env-fixture/env-map]
    (let [token (t/create-token {:alpha 1, :beta true, :gamma "OK"})]
      (is (t/valid-token? token))
      (is (not (t/valid-token? nil)))
      (is (not (t/valid-token? "")))
      (is (not (t/valid-token? "INVALID"))))))

(deftest check-keywordize-properties
  (let [claims (Properties.)
        _ (.setProperty claims "alpha" "one")
        _ (.setProperty claims "beta" "two")]
    (= {:alpha "one" :beta "two"} (t/keywordize-properties claims))))

(deftest check-machine-token
  (with-redefs [environ/env env-fixture/env-map]
    (let [machine-claims (Properties.)
          _ (.setProperty machine-claims "com.sixsq.identifier" "machine")
          _ (.setProperty machine-claims "com.sixsq.roles" "USER ANON")
          _ (.setProperty machine-claims "com.sixsq.machine" "true")]
      (is (nil? (t/-createMachineToken nil)))
      (is (not (str/blank? (t/-createMachineToken machine-claims)))))))

(deftest check-claims-in-token
  (with-redefs [environ/env env-fixture/env-map]
    (let [auth-token (t/create-token {:alpha 1, :beta true, :gamma "OK"})]
      (is (= {"alpha" 1, "beta" true, "gamma" "OK"} (t/-claimsInToken auth-token)))
      (is (= {} (t/-claimsInToken (str auth-token "_MODIFIED"))))
      (is (= {} (t/-claimsInToken nil))))))

