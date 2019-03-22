(ns sixsq.nuvla.auth.password
  (:refer-clojure :exclude [update])
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]))


(defn- extract-credentials
  [request]
  {:username (->> request :params :username)
   :password (uh/param-value request :password)})


(defn identifier->user-id
  [username]
  (try
    (some->
      (db/query
        user-identifier/resource-type
        {:cimi-params {:filter (parser/parse-cimi-filter
                                 (format "identifier='%s'" username))}
         :user-roles  ["ADMIN"]})
      second
      first
      :parent)
    (catch Exception _ {})))


(defn try-retrieve-resource
  [resource-id]
  (when resource-id
    (try
      (db/retrieve resource-id {})
      (catch Exception _ {}))))


(defn user-id->user
  [user-id]
  (try-retrieve-resource user-id))


(defn check-user-active
  [{:keys [state] :as user}]
  (when
    (= state "ACTIVE")
    user))


(defn credential-id->credential
  [credential-id]
  (try-retrieve-resource credential-id))


(defn valid-password?
  [current-password hash]
  (hashers/check current-password hash))


(defn valid-user
  [{identifier       :username
    current-password :password}]
  (let [user (some-> identifier
                     (identifier->user-id)
                     (user-id->user)
                     (check-user-active))
        credential-hash (some-> user
                                :credential-password
                                (credential-id->credential)
                                :hash)]
    (when (valid-password? current-password credential-hash)
      user)))

(defn find-roles-for-user
  [user]
  ;FIXME Should be fetched from new group resource.
  (let [{super? :is-super-user} user]
    (if super?
      "ADMIN USER ANON"
      "USER ANON")))

(defn create-claims
  [{:keys [id] :as user}]
  {:username id                                             ;FIXME What it should be ? array of identifiers
   :roles    (find-roles-for-user user)})
