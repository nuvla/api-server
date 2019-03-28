(ns sixsq.nuvla.auth.utils.sign-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.test :refer :all]
    [environ.core :as environ]
    [sixsq.nuvla.auth.env-fixture :as env-fixture]
    [sixsq.nuvla.auth.utils.sign :as t]
    [sixsq.nuvla.auth.utils.timestamp :as ts]))

(deftest roundtrip-claims
  (let [claims {:alpha "alpha"
                :beta  2
                :gamma 3.0
                :delta true
                :exp   (ts/expiry-later)}]
    (with-redefs [environ/env env-fixture/env-map]
      (is (= claims (t/unsign-cookie-info (t/sign-cookie-info claims)))))))
