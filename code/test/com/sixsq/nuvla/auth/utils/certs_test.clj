(ns com.sixsq.nuvla.auth.utils.certs-test
  (:refer-clojure :exclude [update])
  (:require
    [buddy.core.keys :as ks]
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.auth.env-fixture :as env-fixture]
    [com.sixsq.nuvla.auth.utils.certs :as t]
    [environ.core :as environ]))

(def test-rsa-key "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAosD2Dkf0aa44Q5ur6RNOhVlUbF/kWzQq4UR6nm4cjX1BbnJ+gJdlPYMvg7iu+YCDHPZXERDMXLc4jk3Is9IVtSB2DLbrRYSQGRkHbdo7RF9RZclL1kXlxQUOyU9bvxtbc6oFNuL9WpohEOuPedLvbb5gSNrJaH9gnKkESoMmfViA8O2U4MXcuZ3bbS8spL5LCIPXYDPcpDBjFFvQgFKNvCChb+i6KuU07923T6O0HBkJVmuJ7pRPW6atYADIJ3xYkC5CGE5xqc6KOUibl07DhWP4C8cjN00DdyDazogsKqTXWlFzMOknwlz0fWOtDCvDdvD8AwOsrpU2QAzuLmXDWQIDAQAB")

(deftest test-key-path
  (with-redefs [environ/env {}]
    (is (= t/default-session-crt-path
           (t/key-path :nuvla-session-key t/default-session-crt-path)))
    (is (= t/default-session-key-path
           (t/key-path :nuvla-session-crt t/default-session-key-path))))

  (with-redefs [environ/env env-fixture/env-map]
    (is (= (get env-fixture/env-authn "NUVLA_SESSION_KEY")
           (t/key-path :nuvla-session-key t/default-session-crt-path)))
    (is (= (get env-fixture/env-authn "NUVLA_SESSION_CRT")
           (t/key-path :nuvla-session-crt t/default-session-key-path)))))

(deftest check-read-key
  (with-redefs [environ/env env-fixture/env-map]
    (is (t/read-key ks/private-key t/default-session-crt-path :nuvla-session-key))
    (is (t/private-key :nuvla-session-key))
    (is (t/read-key ks/public-key t/default-session-key-path :nuvla-session-crt))
    (is (t/public-key :nuvla-session-crt))))

(deftest check-throws-unknown-key
  (with-redefs [t/key-path       (fn [_ _] "/unknown/key-path.pem")
                environ.core/env {}]
    (is (thrown? Exception (t/read-key ks/private-key t/default-session-crt-path :nuvla-session-key)))
    (is (thrown? Exception (t/read-key ks/public-key t/default-session-key-path :nuvla-session-crt)))))

(deftest check-parse-key-string
  (is (t/parse-key-string test-rsa-key))
  (is (thrown? Exception (t/parse-key-string (str test-rsa-key "-invalid")))))
