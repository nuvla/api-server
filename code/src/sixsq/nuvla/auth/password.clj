(ns sixsq.nuvla.auth.password
  (:refer-clojure :exclude [update])
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.group :as group]
    [clojure.string :as str]))


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})


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
        (merge admin-opts {:cimi-params {:filter (parser/parse-cimi-filter
                                                   (format "identifier='%s'" username))}}))
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


(defn collect-groups-for-user
  [id]
  (let [group-set (->> (db/query
                         group/resource-type
                         (merge admin-opts
                                {:cimi-params {:filter (parser/parse-cimi-filter (format "users='%s'" id))
                                               :select ["id"]}}))
                       :resources
                       (map :id)
                       (cons "group/nuvla-user")            ;; if there's an id, then the user is authenticated
                       (cons "group/nuvla-anon")            ;; all users are in the nuvla-anon pseudo-group
                       set)]
    (str/join " " (sort (cond-> group-set
                                (group-set "group/nuvla-admin") (conj "ADMIN")
                                (group-set "group/nuvla-user") (conj "USER")
                                (group-set "group/nuvla-anon") (conj "ANON"))))))


(defn create-claims
  [{:keys [id] :as user}]
  {:username id                                             ;FIXME What it should be ? array of identifiers
   :roles    (collect-groups-for-user id)})
