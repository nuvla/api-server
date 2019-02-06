(ns sixsq.nuvla.server.resources.callback-create-session-mitreid
  "Creates a new MITREid session resource presumably after external authentication has succeeded."
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.internal :as auth-internal]
    [sixsq.nuvla.auth.oidc :as auth-oidc]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.util.response :as r]))


(def ^:const action-name "session-mitreid-creation")


(defn validate-session
  [{{session-id :href} :targetResource callback-id :id :as callback-resource} {:keys [base-uri] :as request}]

  (let [{:keys [server clientIP redirectURI] {:keys [href]} :sessionTemplate :as current-session} (sutils/retrieve-session-by-id session-id)
        {:keys [instance]} (crud/retrieve-by-id-as-admin href)
        {:keys [clientID clientSecret publicKey tokenURL]} (oidc-utils/config-mitreid-params redirectURI instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-oidc/get-access-token clientID clientSecret tokenURL code (str base-uri (or callback-id "unknown-id") "/execute"))]
        (try
          (let [{:keys [sub] :as claims} (sign/unsign-claims access-token publicKey)
                roles (concat (oidc-utils/extract-roles claims)
                              (oidc-utils/extract-groups claims)
                              (oidc-utils/extract-entitlements claims))]
            (log/debug "MITREid access token claims for" instance ":" (pr-str claims))
            (if sub
              (if-let [matched-user (ex/match-oidc-username :mitreid sub instance)]
                (let [claims (cond-> (auth-internal/create-claims matched-user)
                                     session-id (assoc :session session-id)
                                     session-id (update :roles #(str session-id " " %))
                                     roles (update :roles #(str % " " (str/join " " roles)))
                                     server (assoc :server server)
                                     clientIP (assoc :clientIP clientIP))
                      cookie (cookies/claims-cookie claims)
                      expires (ts/rfc822->iso8601 (:expires cookie))
                      claims-roles (:roles claims)
                      updated-session (cond-> (assoc current-session :username matched-user :expiry expires)
                                              claims-roles (assoc :roles claims-roles))
                      {:keys [status] :as resp} (sutils/update-session session-id updated-session)]
                  (log/debug "MITREid cookie token claims for" instance ":" (pr-str claims))
                  (if (not= status 200)
                    resp
                    (let [cookie-tuple [(sutils/cookie-name session-id) cookie]]
                      (if redirectURI
                        (r/response-final-redirect redirectURI cookie-tuple)
                        (r/response-created session-id cookie-tuple)))))
                (oidc-utils/throw-inactive-user (str instance ":" sub) redirectURI))
              (oidc-utils/throw-no-subject redirectURI)))
          (catch Exception e
            (oidc-utils/throw-invalid-access-code (str e) redirectURI)))
        (oidc-utils/throw-no-access-token redirectURI))
      (oidc-utils/throw-missing-code redirectURI))))


(defmethod callback/execute action-name
  [{callback-id :id :as callback-resource} request]
  (log/debug "Executing callback" callback-id)
  (try
    (if-let [resp (validate-session callback-resource request)]
      resp
      (do
        (utils/callback-failed! callback-id)
        (r/map-response "could not validate MITREid session" 400)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))

