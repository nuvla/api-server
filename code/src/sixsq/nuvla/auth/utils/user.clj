(ns sixsq.nuvla.auth.utils.user
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.user :as user]))


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
