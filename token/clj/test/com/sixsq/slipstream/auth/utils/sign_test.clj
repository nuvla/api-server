(ns com.sixsq.slipstream.auth.utils.sign-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.auth.env-fixture :as env-fixture]
    [com.sixsq.slipstream.auth.utils.sign :as t]
    [com.sixsq.slipstream.auth.utils.timestamp :as ts]
    [environ.core :as environ]))

(deftest roundtrip-claims
  (let [claims {:alpha "alpha"
                :beta  2
                :gamma 3.0
                :delta true
                :exp   (ts/expiry-later)}]
    (with-redefs [environ/env env-fixture/env-map]
      (is (= claims (t/unsign-claims (t/sign-claims claims)))))))
