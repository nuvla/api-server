(ns sixsq.nuvla.auth.utils.certs
  (:require
    [buddy.core.keys :as keys]
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [environ.core :as environ]))

(def ^:const default-session-key-path "/etc/nuvla/session/session.crt")
(def ^:const default-session-crt-path "/etc/nuvla/session/session.key")


(defn key-path
  [key-env-var default-path]
  (let [path (environ/env key-env-var default-path)]
    (log/info "using key path" path "for" (name key-env-var) "env. variable.")
    path))


(defn read-key
  [read-key-fn default-path key-env-var]
  (try
    (read-key-fn (key-path key-env-var default-path))
    (catch Exception e
      (log/error "error reading key:" (str e))
      (throw e))))


(defn wrap-public-key [public-key]
  (str "-----BEGIN PUBLIC KEY-----\n" public-key "\n-----END PUBLIC KEY-----\n"))


(defn parse-jwk-string
  [jwk-string]
  (try
    (keys/jwk->public-key (json/read-str jwk-string :key-fn keyword))
    (catch Exception e
      (log/debug "error converting jwk to public key: " (str e))
      nil)))


(defn parse-key-string
  [rsa-public-key]
  (try
    (keys/str->public-key (wrap-public-key rsa-public-key))
    (catch Exception e
      (log/error "error reading key:" (str e))
      (throw e))))


(defn parse-public-key
  [key-string]
  (or (parse-jwk-string key-string)
      (parse-key-string key-string)))


(def public-key (memoize (partial read-key keys/public-key default-session-key-path)))


(def private-key (memoize (partial read-key keys/private-key default-session-crt-path)))


(def str->public-key (memoize parse-public-key))
