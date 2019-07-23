(ns sixsq.nuvla.auth.external
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils.user :as auth-user]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


(defn- mapped-user
  [authn-method username]
  (log/infof "External (%s) user '%s' already mapped => login ok." authn-method username)
  username)


(defn- internal-create-user!
  [{:keys [user-id user-identifier email instance authn-method] :as user-data}]
  (log/errorf "creating new user '%s' with identifier '%s' and email '%s'" user-id user-identifier email)
  (let [user {:authn-login  user-identifier
              :username     user-identifier
              :email        email
              :instance     instance
              :authn-method authn-method}]
    (auth-user/create-user! user))
  user-id)


(defn match-existing-external-user
  [authn-method external-login instance]
  (log/debugf "Matching external user with method '%s', external-login '%s', and instance '%s'"
              authn-method external-login instance)
  (when-let [username-mapped (uiu/user-identifier->user-id authn-method instance external-login)]
    (mapped-user authn-method username-mapped)))


(defn match-oidc-username
  [authn-method external-login instance]
  (log/debug "Matching via OIDC/MITREid username" external-login)
  (let [username-by-authn (uiu/user-identifier->user-id authn-method instance external-login)
        username-by-name  (auth-user/get-active-user-by-name external-login)
        username-fallback (when username-by-name (:username (mapped-user instance username-by-name)))]
    (or username-by-authn username-fallback)))


(defn create-user!
  [authn-method {:keys [external-login external-email instance] :as external-record}]

  (let [user-identifier (uiu/generate-identifier authn-method external-login instance)
        id              (u/random-uuid)
        user-id         (str "user/" id)]
    (when-not (or (auth-user/user-exists? user-id)
                  (uiu/user-identifier-exists? user-identifier))
      (internal-create-user! (assoc external-record
                               :authn-login id
                               :user-id user-id
                               :user-identifier user-identifier
                               :email external-email
                               :instance (or instance (name authn-method))
                               :authn-method (name authn-method))))))
