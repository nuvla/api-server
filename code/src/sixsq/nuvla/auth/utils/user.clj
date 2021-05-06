(ns sixsq.nuvla.auth.utils.user
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.user :as user]))


(defn create-user!
  "Create a new user in the database. Values for 'email' and 'user-identifier'
   must be provided. The id of the created user resource is returned."
  ([{:keys [user-identifier email authn-method] :as _user-record}]
   (let [request {:params      {:resource-name user/resource-type}
                  :body        {:template (cond-> {:href "user-template/minimum"}
                                                  user-identifier (assoc :username user-identifier)
                                                  email (assoc :email email)
                                                  authn-method (assoc :method authn-method))}
                  :nuvla/authn auth/internal-identity}
         {{:keys [status resource-id]} :body} (crud/add request)]

     (if (= 201 status)
       (do
         (log/warnf "created %s" resource-id)
         resource-id)
       (do
         (log/errorf "cannot create user for %s" user-identifier)
         nil)))))
