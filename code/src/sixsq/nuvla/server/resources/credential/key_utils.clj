(ns sixsq.nuvla.server.resources.credential.key-utils
  (:require
    [buddy.hashers :as hashers]
    [clojure.string :as str])
  (:import (java.io ByteArrayOutputStream DataOutputStream StringWriter)
           (java.security KeyPairGenerator)
           (java.util Base64)
           (org.bouncycastle.openssl.jcajce JcaPEMWriter)
           (sun.security.rsa RSAPublicKeyImpl)))


;;
;; Use only easily distinguished ASCII letters and numbers for the
;; secret key. Removed characters: 1, l, I, 0, o, and O.
;;
(def ^:const secret-chars (vec "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"))


(def ^:const secret-chars-set (set secret-chars))


(def ^{:doc "Transducer that creates a sequence of five 6-character strings."}
  secret-xform
  (comp (take 30)
        (partition-all 6)
        (map (partial str/join ""))))


(defn strip-invalid-chars
  "Returns the value of the secret with any invalid characters stripped out."
  [secret]
  (str/join "" (keep secret-chars-set secret)))


(defn digest
  "Creates a digest (hash) value of the given secret. Invalid characters in
   the secret are stripped before the digest is calculated."
  [secret]
  (hashers/derive (strip-invalid-chars secret)))


(defn valid?
  "Returns true if the secret matches the digest value; returns false
   otherwise. Invalid characters in the secret are stripped before the
   comparison is made."
  [secret digest]
  (hashers/check (strip-invalid-chars secret) digest))


(defn generate
  "Generates a random string to act as a secret API key and then returns a
   tuple with that string and its digest value."
  []
  (let [secret (->> (repeatedly #(rand-nth secret-chars))
                    (sequence secret-xform)
                    (str/join "."))]
    [secret (digest secret)]))


(defn private-key->string
  [priv-key]
  (let [sw (new StringWriter)]
    (doto (new JcaPEMWriter sw)
      (.writeObject priv-key)
      (.close))
    (-> sw .getBuffer .toString)))


(defn public-key->string
  [^RSAPublicKeyImpl pub-key]
  (let [baos           (new ByteArrayOutputStream)
        ssh-rsa-bytes  (.getBytes "ssh-rsa" "US-ASCII")
        exponent-bytes (.toByteArray (.getPublicExponent pub-key))
        modulus-bytes  (.toByteArray (.getModulus pub-key))]
    (doto (new DataOutputStream baos)
      (.writeInt (alength ssh-rsa-bytes))
      (.write ssh-rsa-bytes)
      (.writeInt (alength exponent-bytes))
      (.write exponent-bytes)
      (.writeInt (alength modulus-bytes))
      (.write modulus-bytes)
      (.close))
    (str "ssh-rsa " (.encodeToString (Base64/getEncoder) (.toByteArray baos)))))


(defn generate-ssh-keypair
  []
  (let [key-gen  (doto (KeyPairGenerator/getInstance "RSA")
                   (.initialize 3072))
        key-pair (.generateKeyPair key-gen)]
    [(-> key-pair .getPublic public-key->string)
     (-> key-pair .getPrivate private-key->string)]))
