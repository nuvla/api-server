(ns sixsq.nuvla.server.resources.callback-create-session-oidc
  "Creates a new OIDC session resource presumably after external authentication has succeeded."
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.oidc :as auth-oidc]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "session-oidc-creation")


(defn validate-session
  [{{session-id :href} :target-resource callback-id :id :as callback-resource} {:keys [base-uri] :as request}]

  (let [{:keys [redirect-url] {:keys [href]} :template :as current-session} (crud/retrieve-by-id-as-admin session-id)
        {:keys [instance]} (crud/retrieve-by-id-as-admin href)
        {:keys [client-id client-secret public-key token-url]} (oidc-utils/config-oidc-params redirect-url instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-oidc/get-access-token client-id client-secret token-url code (str base-uri (or callback-id "unknown-id") "/execute"))]
        (try
          (let [{:keys [sub] :as claims} (sign/unsign-cookie-info access-token public-key)
                roles (concat (oidc-utils/extract-roles claims)
                              (oidc-utils/extract-groups claims)
                              (oidc-utils/extract-entitlements claims))]
            (log/debug "OIDC access token claims for" instance ":" (pr-str claims))
            (if sub
              (if-let [matched-user-id (uiu/user-identifier->user-id :oidc instance sub)]
                (let [{identifier :name} (ex/get-user matched-user-id)
                      cookie-info     (cookies/create-cookie-info matched-user-id
                                                                  :session-id session-id
                                                                  :roles-ext roles)
                      cookie          (cookies/create-cookie cookie-info)
                      expires         (ts/rfc822->iso8601 (:expires cookie))
                      claims-roles    (:claims cookie-info)
                      updated-session (cond-> (assoc current-session
                                                :user matched-user-id
                                                :identifier (or identifier matched-user-id)
                                                :expiry expires)
                                              claims-roles (assoc :roles claims-roles))
                      {:keys [status] :as resp} (sutils/update-session session-id updated-session)]
                  (log/debug "OIDC cookie token claims for" instance ":" (pr-str cookie-info))
                  (if (not= status 200)
                    resp
                    (let [cookie-tuple [authn-info/authn-cookie cookie]]
                      (if redirect-url
                        (r/response-final-redirect redirect-url cookie-tuple)
                        (r/response-created session-id cookie-tuple)))))
                (oidc-utils/throw-inactive-user sub redirect-url))
              (oidc-utils/throw-no-subject redirect-url)))
          (catch Exception e
            (oidc-utils/throw-invalid-access-code (str e) redirect-url)))
        (oidc-utils/throw-no-access-token redirect-url))
      (oidc-utils/throw-missing-code redirect-url))))


(defmethod callback/execute action-name
  [{callback-id :id :as callback-resource} request]
  (log/debug "Executing callback" callback-id)
  (try
    (if-let [resp (validate-session callback-resource request)]
      resp
      (do
        (utils/callback-failed! callback-id)
        (r/map-response "could not validate OIDC session" 400)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))

