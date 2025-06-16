(ns com.sixsq.nuvla.server.resources.credential.encrypt-utils-test
  (:require
    [buddy.core.codecs :as codecs]
    [buddy.core.hash :as hash]
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.credential.encrypt-utils :as t]))

(def key-test (hash/sha256 "asdfioasdfkasio2f8912830ASDFASDFa.$$$@#"))
(def iv-test "QjPVV2kO+C1mmwTZPZw9Yg==")
(def clear-credential {:secret         "this-is-secret"
                       :password       "c0mplicat3d p0ssw0rd"
                       :something-else "abc"})
(def encrypted-credential {:initialization-vector "QjPVV2kO+C1mmwTZPZw9Yg=="
                           :password              "***ENCRYPTED***cgs8XEn2APNIXsxfjc+IOhy/CvmYU/2mf/TWMNMEWV0u6FWB2mOfF7qrL8Q1FlAK"
                           :secret                "***ENCRYPTED***KiSgzk2AzJzzUIOLVsD2blmyaKbdE8AQ0jHFg/pKovU="
                           :something-else        "abc"})
(def request-clear-body-test {:body clear-credential})

(def response-encrypted-body-test {:body encrypted-credential})

(deftest throw-invalid-credential-encryption-key
  (is (= (t/throw-invalid-credential-encryption-key "tooshort`") nil))
  (let [k "123456789012345678901234567890ab"]
    (is (= (t/throw-invalid-credential-encryption-key k) k))))

(deftest encrypt-decrypt
  (let [iv               (t/generate-iv)
        key              key-test
        clear-secret     "some-secret"
        encrypted-secret (t/encrypt clear-secret key iv)]
    (is (= clear-secret (t/decrypt encrypted-secret key iv)))))

(deftest decrypt-credential-secrets-and-remove-iv
  (with-redefs [t/generate-iv (constantly (codecs/b64->bytes iv-test))
                t/ENCRYPTION-KEY key-test]
    (let [result (t/decrypt-credential-secrets-and-remove-iv encrypted-credential)]
      (is (= clear-credential result))))
  (testing "When no initialization-vector is set, decrypt-credential-secrets is a passthrough function"
    (let [result (t/decrypt-credential-secrets-and-remove-iv {:whatever 1})]
      (is (= {:whatever 1} result)))))

(deftest encrypt-request-body-secrets
  (with-redefs [t/generate-iv (constantly (codecs/b64->bytes iv-test))
                t/ENCRYPTION-KEY key-test]
    (let [result (t/encrypt-request-body-secrets request-clear-body-test)]
      (is (= response-encrypted-body-test result))))
  (testing "When no encryption key is set, encrypt is a passthrough function"
    (with-redefs [t/ENCRYPTION-KEY nil]
      (let [result (t/encrypt-request-body-secrets request-clear-body-test)]
       (is (= request-clear-body-test result))))))

(deftest decrypt-response-body-secrets
  (with-redefs [t/ENCRYPTION-KEY key-test]
    (let [result (t/decrypt-response-body-secrets response-encrypted-body-test)]
      (is (= (assoc-in request-clear-body-test [:body :initialization-vector] iv-test) result)))
    (testing "When secrets are not containing the encrypted indicator text, secret is returned as is"
      (let [secret-text "secret without encryption indicator"
            result      (t/decrypt-response-body-secrets (assoc-in response-encrypted-body-test [:body :secret] secret-text))]
        (is (= (-> request-clear-body-test
                   (assoc-in [:body :initialization-vector] iv-test)
                   (assoc-in [:body :secret] secret-text)) result)))))
  (with-redefs [t/ENCRYPTION-KEY nil]
    (testing "When no encryption key is set, decrypt is a passthrough function"
      (let [result (t/decrypt-response-body-secrets response-encrypted-body-test)]
        (is (= response-encrypted-body-test result))))))

(deftest decrypt-response-body-secrets
  (let [empty-query-response {:body {:resources []}}]
    (with-redefs [t/ENCRYPTION-KEY key-test]
      (is (= empty-query-response (t/decrypt-response-query-credentials empty-query-response)))

      (let [query-response {:body {:resources [encrypted-credential {:whatever 1} encrypted-credential]}}
            result {:body {:resources [clear-credential {:whatever 1} clear-credential]}}]
        (is (= result (t/decrypt-response-query-credentials query-response))))))

  (with-redefs [t/ENCRYPTION-KEY nil]
    (testing "When no encryption key is set, decrypt-response-query-credentials is a passthrough function"
      (let [result (t/decrypt-response-query-credentials {:whatever 1})]
        (is (= {:whatever 1} result))))))
