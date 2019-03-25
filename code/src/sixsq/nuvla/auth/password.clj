(ns sixsq.nuvla.auth.password
  (:refer-clojure :exclude [update])
  (:require
    [buddy.hashers :as hashers]
    [clojure.string :as str]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]))


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})


(defn identifier->user-id
  [username]
  (try
    (let [f (parser/parse-cimi-filter (format "identifier='%s'" username))
          opts (merge admin-opts {:cimi-params {:filter f}})]
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


;; FIXME: This should call the check-password action on the credential instead of checking locally.
(defn valid-user
  [{:keys [username password] :as credentials}]
  (let [user (some-> username
                     identifier->user-id
                     user-id->user
                     check-user-active)
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
                         (merge admin-opts
                                {:cimi-params {:filter (parser/parse-cimi-filter (format "users='%s'" id))
                                               :select ["id"]}}))
                       second
                       (map :id)
                       (cons "group/nuvla-user")            ;; if there's an id, then the user is authenticated
                       (cons "group/nuvla-anon")            ;; all users are in the nuvla-anon pseudo-group
                       set)]
    ;; FIXME: Remove addition of ADMIN, USER, and ANON when we directly use the group information.
    (str/join " " (sort (cond-> group-set
                                (group-set "group/nuvla-admin") (conj "ADMIN")
                                (group-set "group/nuvla-user") (conj "USER")
                                (group-set "group/nuvla-anon") (conj "ANON"))))))


(defn create-claims
  [{:keys [id] :as user}]
  {:username id
   :roles    (collect-groups-for-user id)})
