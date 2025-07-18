(ns com.sixsq.nuvla.server.resources.credential.encrypt-utils
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.crypto :as crypto]
            [buddy.core.hash :as hash]
            [buddy.core.nonce :as nonce]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :as env]))

(def encrypted-starter-indicator "***ENCRYPTED***")
(def secret-keys [:secret :password :vpn-certificate :key :private-key :token :secret-key])

(defn throw-invalid-credential-encryption-key
  [key]
  (if (> (count key) 31)
    key
    (log/error "Credential encryption key size must be bigger than 31 characters!")))

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

(defn encrypt-request-body-secrets
  [{{:keys [initialization-vector] :as body} :body :as request}]
  (if ENCRYPTION-KEY
    (let [secrets-entries   (select-keys body secret-keys)
          iv                (or (some-> initialization-vector codecs/b64->bytes) (generate-iv))
          encrypted-entries (reduce-kv (fn [acc k v]
                                         (assoc acc k (str encrypted-starter-indicator (encrypt v ENCRYPTION-KEY iv))))
                                       {}
                                       secrets-entries)]
      (update request :body merge encrypted-entries {:initialization-vector (codecs/bytes->b64-str iv)}))
    request))

(defn decrypt-credential-secrets
  [{:keys [id initialization-vector] :as credential}]
  (let [iv (try
             (codecs/b64->bytes initialization-vector)
             (catch AssertionError e
               (log/error "Failed initialization-vector encoding " id ":" (ex-message e))))]
    (if (and ENCRYPTION-KEY iv)
     (let [secrets-entries   (->> (select-keys credential secret-keys)
                                  (filter (fn [[_ v]] (str/starts-with? v encrypted-starter-indicator)))
                                  (into {}))
           decrypted-entries (reduce-kv (fn [acc k v]
                                          (let [encrypted-text (subs v (count encrypted-starter-indicator))
                                                decrypt-result (try
                                                                 (decrypt encrypted-text ENCRYPTION-KEY iv)
                                                                 (catch Exception e
                                                                   (log/error "Failed to decrypt " id k ":" (ex-message e))
                                                                   v))]
                                            (assoc acc k decrypt-result)))
                                        {}
                                        secrets-entries)]
       (merge credential decrypted-entries))
     credential)))

(defn decrypt-credential-secrets-and-remove-iv
  [credential]
  (dissoc (decrypt-credential-secrets credential) :initialization-vector))

(defn decrypt-response-body-secrets
  [response]
  (update response :body decrypt-credential-secrets-and-remove-iv))

(defn decrypt-response-query-credentials
  [{{:keys [resources]} :body :as response}]
  (if ENCRYPTION-KEY
    (assoc-in response [:body :resources] (map decrypt-credential-secrets-and-remove-iv resources))
    response))
