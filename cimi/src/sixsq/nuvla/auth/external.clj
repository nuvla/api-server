(ns sixsq.nuvla.auth.external
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils.db :as db]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


(defn- mapped-user
  [authn-method username]
  (log/infof "External (%s) user '%s' already mapped => login ok." authn-method username)
  username)


(defn- map-slipstream-user!
  [authn-method slipstream-username external-login]
  (log/infof "Mapping external (%s) user '%s' to existing SlipStream user '%s'"
             authn-method external-login slipstream-username)
  (db/update-user-authn-info authn-method slipstream-username external-login))


(defn- create-slipstream-user!
  ([authn-method external-login external-email instance]
   (log/infof "Creating new SlipStream user with external (%s) user '%s'"
              authn-method external-login)
   (let [user-name (db/create-user! authn-method external-login external-email instance)]
     (when user-name
       (db/create-user-params! user-name))
     user-name))

  ([{:keys [authn-login] :as user-record}]
   (log/infof "Creating new SlipStream user '%s'" authn-login)
   (let [user-name (db/create-user! user-record)]
     (when user-name
       (db/create-user-params! user-name))
     user-name)))


(defn match-existing-external-user
  [authn-method external-login instance]
  (log/debugf "Matching external user with method '%s', external-login '%s', and instance '%s'"
              authn-method external-login instance)
  (when-let [username-mapped (uiu/find-username-by-identifier authn-method instance external-login)]
    (mapped-user authn-method username-mapped)))


(defn match-oidc-username
  [authn-method external-login instance]
  (log/debug "Matching via OIDC/MITREid username" external-login)
  (let [username-by-authn (uiu/find-username-by-identifier authn-method instance external-login)
        username-by-name (db/get-active-user-by-name external-login)
        username-fallback (when username-by-name (:username (mapped-user instance username-by-name)))]
    (or username-by-authn username-fallback)))


(defn create-user-when-missing!
  [authn-method {:keys [external-login external-email instance fail-on-existing?] :as external-record}]

  (let [username-by-authn (uiu/find-username-by-identifier authn-method instance external-login)
        username (u/random-uuid)]
    (if (and username-by-authn (not fail-on-existing?))
      username-by-authn
      (when-not username-by-authn (if-not
                                    (or (db/user-exists? (or external-login username))
                                        (uiu/user-identity-exists? authn-method (or external-login username)))
                                    (create-slipstream-user! (assoc external-record
                                                               :authn-login username
                                                               :external-login external-login
                                                               :email external-email
                                                               :instance (or instance (name authn-method))
                                                               :authn-method (name authn-method)))
                                    (when-not fail-on-existing?
                                      (or external-login username)))))))
