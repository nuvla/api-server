(ns com.sixsq.slipstream.ssclj.resources.credential.ssh-utils
  (:refer-clojure :exclude [load])
  (:require
    [clojure.string :as str])
  (:import (com.jcraft.jsch JSch KeyPair)
           (java.io ByteArrayOutputStream)))

(def ^:const default-key-size 1024)

(def ^:const default-algorithm :rsa)

(defn- get-public-key [^KeyPair kp]
  (with-open [baos (ByteArrayOutputStream.)]
    (let [fingerprint (.getFingerPrint kp)]
      (.writePublicKey kp baos fingerprint)
      (.toString baos "UTF-8"))))

(defn- get-private-key [^KeyPair kp]
  (with-open [baos (ByteArrayOutputStream.)]
    (.writePrivateKey kp baos)
    (.toString baos "UTF-8")))

(def key-name-to-index {"rsa" KeyPair/RSA
                        "dsa" KeyPair/DSA})

(def key-index-to-name {KeyPair/RSA "rsa"
                        KeyPair/DSA "dsa"})

(defn generate
  "Generates a new SSH key pair with the given algorithm and length. The
   algorithm can either be 'rsa' (default) or 'dsa'. The key size will default
   to 1024 bits. Note that not all cloud providers support DSA keys."
  ([]
   (generate default-algorithm default-key-size))
  ([algorithm]
   (generate algorithm default-key-size))
  ([algorithm size]
   (let [algorithm-key (-> algorithm name str/lower-case)]
     (if-let [algorithm-const (key-name-to-index algorithm-key)]
       (try
         (let [kp (KeyPair/genKeyPair (JSch.) algorithm-const size)
               fingerprint (.getFingerPrint kp)]
           {:algorithm   algorithm
            :fingerprint fingerprint
            :publicKey   (get-public-key kp)
            :privateKey  (get-private-key kp)})
         (catch Exception e
           (throw (ex-info (str e) {}))))
       (throw (ex-info (str "invalid key algorithm: " algorithm-key) {}))))))

(defn load
  "Loads a public key (validating it in the process) and then returns a map
   with the public key information. The public-key parameter must be a string
   representation of the public key in a supported format."
  [public-key]
  (let [^bytes bytes (.getBytes public-key "UTF-8")]
    (try
      (let [kp (KeyPair/load (JSch.) nil bytes)
            key-algorithm (.getKeyType kp)]
        (if-let [algorithm-name (key-index-to-name key-algorithm)]
          {:algorithm   algorithm-name
           :fingerprint (.getFingerPrint kp)
           :publicKey   (get-public-key kp)}
          (throw (ex-info (str "unsupported public key algorithm: " key-algorithm) {}))))
      (catch Exception e
        (throw (ex-info "invalid public key" {}))))))
