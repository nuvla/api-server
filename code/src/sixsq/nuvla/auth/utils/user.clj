(ns sixsq.nuvla.auth.utils.user
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.user :as user]))


;; Only ACTIVE users can log in.  All other states (NEW, SUSPENDED) are disallowed.
(def ^:private active-user-filter "(state='ACTIVE')")


(defn get-user
  [username]
  (try
    (db/retrieve (str user/resource-type "/" username) {})
    (catch Exception _ {})))


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


(defn create-user!
  "Create a new user in the database. Values for 'email' and 'authn-login'
   must be provided. The value used to create the account is returned."
  ([{:keys [user-identifier email authn-method] :as user-record}]
   (let [request {:params      {:resource-name user/resource-type}
                  :body        {:template (cond-> {:href "user-template/minimum"}
                                                  user-identifier (assoc :username user-identifier)
                                                  email (assoc :email email)
                                                  authn-method (assoc :method authn-method))}
                  :nuvla/authn auth/internal-identity}
         {{:keys [status resource-id] :as body} :body} (crud/add request)]

     (if (not= 201 status)
       (throw (ex-info (str "cannot create user for " user-identifier) user-record))
       (do
         (log/errorf "created %s" resource-id)
         resource-id)))))
