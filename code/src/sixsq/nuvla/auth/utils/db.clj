(ns sixsq.nuvla.auth.utils.db
  (:require
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud])
  (:import
    (java.util UUID)))


;; Only ACTIVE users can log in.  All other states (NEW, SUSPENDED, and DELETED) are disallowed
(def ^:private active-user-filter "(state='ACTIVE')")


(def resource-name "user")


(defn resource-uri
  [name]
  (str resource-name "/" name))


(defn get-all-users
  []
  (try
    (second (crud/query-as-admin resource-name nil))
    (catch Exception _ [])))


;; FIXME: user-param resource doesn't exist; remove this function!
(defn get-all-user-params
  []
  (try
    (second (crud/query-as-admin :user-param nil))
    (catch Exception _ [])))


(defn get-active-users
  []
  (let [filter {:filter (parser/parse-cimi-filter active-user-filter)}]
    (try
      (second (crud/query-as-admin resource-name {:cimi-params filter}))
      (catch Exception _ []))))


(defn get-user
  [username]
  (try
    (db/retrieve (resource-uri username) {})
    (catch Exception _ {})))


(defn find-usernames-by-email
  [email]
  (when email
    (let [filter-str    (format "emailAddress='%s' and %s" email active-user-filter)
          filter        {:filter (parser/parse-cimi-filter filter-str)}
          matched-users (try (second (crud/query-as-admin resource-name {:cimi-params filter}))
                             (catch Exception _ []))]
      (set (map :username matched-users)))))


(defn- to-am-kw
  [authn-method]
  (keyword (str (name authn-method) "login")))



(defn get-active-user-by-name
  [username]
  (when username
    (let [filter-str (format "username='%s' and %s" username active-user-filter)
          filter     {:filter (parser/parse-cimi-filter filter-str)}]
      (try (-> (crud/query-as-admin :user {:cimi-params filter})
               second
               first)
           (catch Exception _ {})))))


(defn user-exists?
  "Verifies that a user with the given username exists in the database no
   matter what the user state is."
  [username]
  (-> username get-user :state nil? not))


(defn- to-resource-id
  [n]
  (format "%s/%s" resource-name n))


(defn update-user-authn-info
  [authn-method slipstream-username authn-id]
  (let [body    {:id                     (to-resource-id slipstream-username)
                 :username               slipstream-username
                 (to-am-kw authn-method) authn-id}
        request {:identity       {:current slipstream-username
                                  :authentications
                                           {slipstream-username {:roles #{"USER"} :identity slipstream-username}}}
                 :sixsq.slipstream.authn/claims
                                 {:username slipstream-username :roles "USER"}
                 :params         {:resource-name resource-name}
                 :route-params   {:resource-name resource-name}
                 :user-roles     #{"ANON"}
                 :uri            (format "/api/%s/%s" resource-name slipstream-username)
                 :request-method "PUT"
                 :body           body}]
    (crud/edit request))
  slipstream-username)


(defn existing-user-names
  []
  (let [users (second (crud/query-as-admin :user nil))]
    (set (map :username users))))


(defn random-password
  []
  (str (UUID/randomUUID)))


(defn user-create-request
  [{:keys [authn-login email authn-method firstname lastname roles organization state external-login password instance] :as user-record}]
  (let [user-resource (cond-> {:href         "user-template/direct" ;; FIXME: should reflect the actual user template
                               :username     authn-login
                               :emailAddress email
                               :password     (if password password (random-password))
                               :deleted      false
                               :isSuperUser  false
                               :state        (or state "ACTIVE")}
                              authn-method (assoc :method authn-method
                                                  :name email)
                              firstname (assoc :firstName firstname)
                              lastname (assoc :lastName lastname)
                              roles (assoc :roles roles)
                              organization (assoc :organization organization))]
    {:identity     {:current "internal"
                    :authentications
                             {"internal" {:roles #{"ADMIN"}, :identity "internal"}}}
     :sixsq.slipstream.authn/claims
                   {:username "internal", :roles "ADMIN"}
     :params       {:resource-name "user"}
     :route-params {:resource-name "user"}
     :user-roles   #{"ANON"}
     :body         {:userTemplate user-resource}}))


(defn create-user!
  "Create a new user in the database. Values for 'email' and 'authn-login'
   must be provided. NOTE: The 'authn-login' value may be modified to avoid
   collisions with existing users. The value used to create the account is
   returned."
  ([{:keys [authn-login fail-on-existing?] :as user-record}]
   (if ((existing-user-names) authn-login)
     (when (not fail-on-existing?)
       authn-login)
     (do
       (crud/add (user-create-request user-record))
       authn-login)))

  ([authn-method authn-login email external-login]
   (create-user! {:authn-login    authn-login               ;; possibly a UUID
                  :authn-method   authn-method
                  :email          email
                  :external-login external-login}))

  ([authn-method authn-login email]
   (create-user! {:authn-login    authn-login
                  :authn-method   authn-method
                  :email          email
                  :external-login authn-login}))            ;; legacy behaviour where external-login=authn-login

  ([authn-login email]
   (create-user! {:authn-login authn-login
                  :email       email})))


(defn find-password-for-username
  [username]
  (:password (get-active-user-by-name username)))


(defn find-roles-for-username
  [username]
  (let [{super? :isSuperUser} (get-active-user-by-name username)]
    (if super? "ADMIN USER ANON" "USER ANON")))


