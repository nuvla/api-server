(ns sixsq.nuvla.auth.utils
  (:require [clojure.string :as str]))

(defn current-authentication
  "Extracts the current authentication from the ring request."
  [{:keys [nuvla/authn] :as request}]
  (select-keys authn [:user-id :claims]))


(defn current-session-id
  [request]
  (->> request
       current-authentication
       :claims
       (filter #(str/starts-with? % "session/"))
       first))


(def ^{:doc "Internal administrator identity for database queries."}
  internal-identity
  {:user-id "internal"
   :claims  #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"}})
