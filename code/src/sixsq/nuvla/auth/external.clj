(ns sixsq.nuvla.auth.external
  (:require
    [sixsq.nuvla.auth.utils.user :as auth-user]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.resources.common.crud :as crud]))


(defn get-user
  [user-id]
  (try
    (when user-id
      (crud/retrieve-by-id-as-admin user-id))
    (catch Exception _ nil)))


(defn create-user!
  [authn-method {:keys [external-id external-email instance]}]
  (let [user-identifier (uiu/generate-identifier authn-method instance external-id)]
    (when-not (uiu/user-identifier-exists? user-identifier)
      (auth-user/create-user! {:user-identifier user-identifier
                               :email           external-email
                               :authn-method    (name authn-method)}))))
