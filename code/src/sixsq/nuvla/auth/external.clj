(ns sixsq.nuvla.auth.external
  (:require
    [sixsq.nuvla.auth.utils.user :as auth-user]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


(defn create-user!
  [authn-method {:keys [external-id external-email instance]}]
  (let [user-identifier (uiu/generate-identifier authn-method external-id instance)]
    (when-not (uiu/user-identifier-exists? user-identifier)
      (auth-user/create-user! {:user-identifier user-identifier
                               :email           external-email
                               :authn-method    (name authn-method)}))))
