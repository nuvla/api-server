(ns sixsq.nuvla.auth.utils
  (:require
    [clojure.string :as str]))


(def ^{:doc "Internal administrator identity for database queries."}
  internal-identity
  {:user-id      "internal"
   :active-claim "internal"
   :claims       #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"}})


(defn current-authentication
  "Extracts the current authentication from the ring request."
  [{:keys [nuvla/authn] :as request}]
  (select-keys authn [:user-id :active-claim :claims :groups]))


(defn current-user-id
  [request]
  (:user-id (current-authentication request)))


(defn current-active-claim
  [request]
  (:active-claim (current-authentication request)))


(defn current-claims
  [request]
  (:claims (current-authentication request)))


(defn current-groups
  [request]
  (:groups (current-authentication request)))


(defn current-session-id
  [request]
  (->> request
       current-authentication
       :claims
       (filter #(str/starts-with? % "session/"))
       first))


