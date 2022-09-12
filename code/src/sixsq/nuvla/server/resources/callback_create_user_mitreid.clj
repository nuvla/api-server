(ns sixsq.nuvla.server.resources.callback-create-user-mitreid
  "Creates a new MITREid user resource presumably after external authentication has succeeded."
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
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "user-mitreid-creation")


(defn register-user
  [{{:keys [href]} :target-resource {:keys [redirect-url]} :data callback-id :id :as _callback-resource}
   {:keys [base-uri] :as request}]
  (let [{:keys [instance]} (crud/retrieve-by-id-as-admin href)
        {:keys [client-id client-secret public-key token-url user-profile-url]} (oidc-utils/config-mitreid-params redirect-url instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-oidc/get-id-token client-id client-secret token-url code (str base-uri (or callback-id "unknown-id") "/execute"))]
        (try
          (let [{:keys [sub] :as claims} (sign/unsign-cookie-info access-token public-key)]
            (log/debugf "MITREid access token claims for %s: %s" instance (pr-str claims))
            (if sub
              (let [{:keys [emails] :as _userinfo} (oidc-utils/get-mitreid-userinfo user-profile-url access-token)
                    email (->> emails (filter :primary) first :value)]
                (if email
                  (or
                    (ex/create-user! :mitreid {:instance       instance
                                               :external-id    sub
                                               :external-email email})
                    (oidc-utils/throw-user-exists redirect-url))
                  (oidc-utils/throw-no-email redirect-url)))
              (oidc-utils/throw-no-subject redirect-url)))
          (catch Exception e
            (oidc-utils/throw-invalid-access-code (str e) redirect-url)))
        (oidc-utils/throw-no-access-token redirect-url))
      (oidc-utils/throw-missing-code redirect-url))))


(defmethod callback/execute action-name
  [{callback-id :id {:keys [redirect-url]} :data :as callback-resource} request]
  (log/debug "Executing callback" callback-id)
  (try
    (if-let [user-id (register-user callback-resource request)]
      (do
        (utils/callback-succeeded! callback-id)
        (if redirect-url
          (r/map-response (format "'%s' created" user-id) 303 callback-id redirect-url)
          (r/map-response (format "'%s' created" user-id) 201)))
      (do
        (utils/callback-failed! callback-id)
        (r/map-response "could not create MITREid user" 400)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))

