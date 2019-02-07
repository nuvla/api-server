(ns sixsq.nuvla.server.resources.callback-create-user-oidc
  "Creates a new OIDC user resource presumably after external authentication has succeeded."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.oidc :as auth-oidc]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.util.response :as r]))


(def ^:const action-name "user-oidc-creation")


(defn register-user
  [{{href :href} :targetResource {:keys [redirectURI]} :data callback-id :id :as callback-resource} {:keys [base-uri] :as request}]
  (let [{:keys [instance]} (crud/retrieve-by-id-as-admin href)
        {:keys [clientID clientSecret publicKey tokenURL]} (oidc-utils/config-oidc-params redirectURI instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-oidc/get-access-token clientID clientSecret tokenURL code (str base-uri (or callback-id "unknown-id") "/execute"))]
        (try
          (let [{:keys [sub email given_name family_name realm] :as claims} (sign/unsign-claims access-token publicKey)]
            (log/debugf "oidc access token claims for %s: %s" instance (pr-str claims))
            (if sub
              (if-let [matched-user (ex/create-user-when-missing! :oidc {:external-login    sub
                                                                         :external-email    (or email (str sub "@fake-email.com")) ;;some OIDC server do not return emails
                                                                         :firstname         given_name
                                                                         :lastname          family_name
                                                                         :instance          instance
                                                                         :fail-on-existing? true})]
                (do
                  (uiu/add-user-identifier! matched-user :oidc sub instance)
                  matched-user)
                (oidc-utils/throw-user-exists sub redirectURI))
              (oidc-utils/throw-no-subject redirectURI)))
          (catch Exception e
            (oidc-utils/throw-invalid-access-code (str e) redirectURI)))
        (oidc-utils/throw-no-access-token redirectURI))
      (oidc-utils/throw-missing-code redirectURI))))


(defmethod callback/execute action-name
  [{callback-id :id {:keys [redirectURI]} :data :as callback-resource} request]
  (log/debug "Executing callback" callback-id)
  (try
    (if-let [username (register-user callback-resource request)]
      (do
        (utils/callback-succeeded! callback-id)
        (if redirectURI
          (r/map-response (format "user '%s' created" username) 303 callback-id redirectURI)
          (r/map-response (format "user '%s' created" username) 201)))
      (do
        (utils/callback-failed! callback-id)
        (r/map-response "could not create OIDC user" 400)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))

