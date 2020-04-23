(ns sixsq.nuvla.auth.password
  (:refer-clojure :exclude [update])
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]))


(defn identifier->user-id
  [username]
  (try
    (let [f    (parser/parse-cimi-filter (format "identifier='%s'" username))
          opts {:cimi-params {:filter f}}]
      (some-> (crud/query-as-admin user-identifier/resource-type opts)
              second
              first
              :parent))
    (catch Exception _ nil)))


(defn try-retrieve-resource
  [resource-id]
  (when resource-id
    (try
      (db/retrieve resource-id {})
      (catch Exception _ nil))))


(defn user-id->user
  [user-id]
  (try-retrieve-resource user-id))


(defn check-user-active
  [{:keys [state] :as user}]
  (when (= state "ACTIVE")
    user))


(defn credential-id->credential
  [credential-id]
  (try-retrieve-resource credential-id))


(defn valid-password?
  [current-password hash]
  (try
    (boolean (hashers/check current-password hash))
    (catch Exception _
      false)))


(defn extract-user
  [username]
  (some-> username
          identifier->user-id
          user-id->user))


(defn active-user
  [username]
  (-> username extract-user check-user-active))


;; FIXME: This should call the check-password action on the credential instead of checking locally.
(defn valid-user-password
  [username password]
  (let [user          (active-user username)
        password-hash (some-> user
                              :credential-password
                              credential-id->credential
                              :hash)]
    (when (valid-password? password password-hash)
      user)))
