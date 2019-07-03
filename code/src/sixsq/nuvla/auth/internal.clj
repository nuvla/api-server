(ns sixsq.nuvla.auth.internal
  (:refer-clojure :exclude [update])
  (:require
    [buddy.core.codecs :as co]

    [buddy.core.hash :as ha]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.utils.db :as db]
    [sixsq.nuvla.auth.utils.http :as uh]))


(defn- extract-credentials
  [request]
  ;; FIXME: Remove :user-name!
  {:username (->> request :params ((some-fn :username :user-name)))
   :password (uh/param-value request :password)})


(defn hash-password
  "Hash password exactly as done in SlipStream Java server."
  [password]
  (when password
    (-> (ha/sha512 password)
        co/bytes->hex
        str/upper-case)))


(defn valid?
  [{:keys [username password]}]
  (let [db-password-hash (db/find-password-for-username username)]
    (and
      password
      db-password-hash
      (= (hash-password password) db-password-hash))))


(defn create-claims
  [username]
  {:username username
   :roles    (db/find-roles-for-username username)})


(defn login
  [request]
  (let [{:keys [username] :as credentials} (extract-credentials request)]
    (if (valid? credentials)
      (do
        (log/info "successful login for" username)
        (assoc
          (uh/response 200)
          :cookies (cookies/create-cookie (create-claims username))))
      (do
        (log/warn "failed login attempt for" username)
        (uh/response-forbidden)))))                         ;; FIXME: Returns 401, but should be 403.


(defn logout
  []
  (log/info "sending logout cookie")
  (assoc
    (uh/response 200)
    :cookies (cookies/revoked-cookie "sixsq.nuvla.cookie")))
