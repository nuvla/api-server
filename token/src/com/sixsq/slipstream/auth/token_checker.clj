(ns com.sixsq.slipstream.auth.token-checker
  (:require
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [com.sixsq.slipstream.auth.utils.sign :as sign])
  (:import [java.util Properties])
  (:gen-class
    :name com.sixsq.slipstream.auth.TokenChecker
    :methods [^:static [createMachineToken [java.util.Properties] String]
              ^:static [claimsInToken [String] java.util.Map]]))


(defn create-token
  [claims]
  (try
    (when claims
      (sign/sign-claims claims))
    (catch Exception e
      (log/error "error signing machine token claims:" (str e))
      nil)))


(defn valid-token?
  [token]
  (try
    (sign/unsign-claims token)
    true
    (catch Exception e
      (log/error "invalid authn token when creating machine token:" (str e))
      false)))


(defn keywordize-properties
  "Copies properties into a persistent hash map while changing keys to keywords."
  [^Properties claims]
  (into {} (map (fn [[k v]] [(keyword k) v]) claims)))


(defn -createMachineToken
  "Signs the claims for a machine token."
  [^Properties claims]
  (some-> claims
          keywordize-properties
          create-token))


(defn -claimsInToken
  "Validates the given token and returns the embedded claims. If the token is
   not valid, then a warning is logged and an empty claims map is returned.
   This method facilitates token checking from Java. For clojure, use the
   validation functions directly. An empty claims map will be silently returned
   if the token is nil."
  [^String token]
  (try
    (if token
      (-> token
          sign/unsign-claims
          walk/stringify-keys)
      {})
    (catch Exception e
      (log/warn "invalid authentication token: " token ", cause" e)
      {})))
