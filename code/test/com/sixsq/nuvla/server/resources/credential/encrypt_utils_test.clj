(ns com.sixsq.nuvla.server.resources.credential.encrypt-utils-test
  (:require
    [buddy.core.hash :as hash]
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.credential.encrypt-utils :as t]))


(deftest throw-invalid-credential-encryption-key
  (is (thrown-with-msg? Exception #"Credential encryption key size must be bigger than 31 characters!" (t/throw-invalid-credential-encryption-key "tooshort")))

  (let [k "123456789012345678901234567890ab"]
    (is (= (t/throw-invalid-credential-encryption-key k) k))))

(deftest encrypt-decrypt
  (let [iv               (t/generate-iv)
        key              (hash/sha256 "asdfioasdfkasio2f8912830ASDFASDFa.$$$@#")
        clear-secret     "some-secret"
        encrypted-secret (t/encrypt clear-secret key iv)]
    (is (= clear-secret (t/decrypt encrypted-secret key iv)))))
