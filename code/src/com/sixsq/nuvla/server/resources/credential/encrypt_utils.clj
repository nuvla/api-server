(ns com.sixsq.nuvla.server.resources.credential.encrypt-utils
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.crypto :as crypto]
            [buddy.core.hash :as hash]
            [buddy.core.nonce :as nonce]
            [environ.core :as env]))

(defn throw-invalid-credential-encryption-key
  [key]
  (if (> (count key) 31)
    key
    (throw (ex-info "Credential encryption key size must be bigger than 31 characters!" {}))))

(defonce ENCRYPTION-KEY (some-> (env/env :credential-encryption-key) throw-invalid-credential-encryption-key hash/sha256))

(def alg {:alg :aes128-cbc-hmac-sha256})

(defn generate-iv
  []
  (nonce/random-bytes 16))

(defn encrypt
  [text key iv]
  (-> (codecs/str->bytes text)
      (crypto/encrypt key iv alg)
      codecs/bytes->b64-str))

(defn decrypt
  [encrypted-text key iv]
  (-> (codecs/b64->bytes encrypted-text)
      (crypto/decrypt key iv alg)
      codecs/bytes->str))

(defn encrypt-body-secrets
  [_]

  )

(defn decrypt-body-secrets
  [_]
  )
