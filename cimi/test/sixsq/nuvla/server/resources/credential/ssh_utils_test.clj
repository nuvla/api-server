(ns sixsq.nuvla.server.resources.credential.ssh-utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.credential.ssh-utils :as t])
  (:import
    (clojure.lang ExceptionInfo)))

(deftest check-generate
  (doseq [type #{"rsa" "dsa"}
          size #{1024 2048}]
    (let [kp (t/generate type size)]
      (is (= type (:algorithm kp)))
      (is (re-matches #"^[0-9a-f]{2}(:[0-9a-f]{2}){15}$" (:fingerprint kp)))
      (is (:publicKey kp))
      (is (:privateKey kp)))))

(deftest check-generate-failures
  (is (thrown-with-msg? ExceptionInfo #"invalid key algorithm: unknown" (t/generate :unknown 1024)))
  (is (thrown-with-msg? ExceptionInfo #".*RSA keys must be at least 512 bits long.*" (t/generate :rsa 0))))

(deftest check-load
  (doseq [type #{"rsa" "dsa"}
          size #{1024 2048}]
    (let [kp (t/generate type size)
          loaded-kp (t/load (:publicKey kp))]
      (is (= loaded-kp (dissoc kp :privateKey))))))

(deftest check-load-failures
  (is (thrown-with-msg? ExceptionInfo #"invalid public key" (t/load "invalid-ssh-key"))))
