(ns sixsq.nuvla.auth.password
  (:refer-clojure :exclude [update])
  (:require
    [buddy.hashers :as hashers]
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]))


(defn identifier->user-id
  [username]
  (try
    (let [f    (parser/parse-cimi-filter (format "identifier='%s'" username))
          opts {:cimi-params {:filter f}
                :nuvla/authn auth/internal-identity}]
      (some-> (db/query user-identifier/resource-type opts)
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
  (-> (extract-user username)
      (check-user-active)))


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


(defn collect-groups-for-user
  [id]
  (let [group-set (->> (db/query
                         group/resource-type
                         {:cimi-params {:filter (parser/parse-cimi-filter (format "users='%s'" id))
                                        :select ["id"]}
                          :nuvla/authn auth/internal-identity})
                       second
                       (map :id)
                       (cons "group/nuvla-user")            ;; if there's an id, then the user is authenticated
                       (cons "group/nuvla-anon")            ;; all users are in the nuvla-anon pseudo-group
                       set)]
    (str/join " " (sort group-set))))


(defn create-claims
  [{:keys [id] :as user}]
  {:user-id id
   :claims  (collect-groups-for-user id)})
