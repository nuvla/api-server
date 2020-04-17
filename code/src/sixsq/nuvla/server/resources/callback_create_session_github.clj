(ns sixsq.nuvla.server.resources.callback-create-session-github
  "Creates a new Github session resource presumably after external
   authentication has succeeded."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.github :as auth-github]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.github.utils :as gu]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "session-github-creation")


(defn validate-session
  [request session-id]
  (let [{:keys [redirect-url] {:keys [href]} :template :as current-session} (crud/retrieve-by-id-as-admin session-id)
        {:keys [instance]} (crud/retrieve-by-id-as-admin href)
        [client-id client-secret] (gu/config-github-params redirect-url instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-github/get-github-access-token client-id client-secret code)]
        (if-let [user-info (auth-github/get-github-user-info access-token)]
          (do
            (log/debug "github user info for" instance ":" user-info)
            (let [external-login  (:login user-info)
                  matched-user-id (uiu/user-identifier->user-id :github instance external-login)]
              (if matched-user-id
                (let [{identifier :name} (ex/get-user matched-user-id)
                      cookie-info     (cookies/create-cookie-info matched-user-id
                                                                 :session-id session-id)
                      cookie          (cookies/create-cookie cookie-info)
                      expires         (ts/rfc822->iso8601 (:expires cookie))
                      claims-roles    (:claims cookie-info)
                      updated-session (cond-> (assoc current-session
                                                :user matched-user-id
                                                :identifier (or identifier matched-user-id)
                                                :expiry expires)
                                              claims-roles (assoc :roles claims-roles))
                      {:keys [status] :as resp} (sutils/update-session session-id updated-session)]
                  (log/debug "github cookie token claims for" instance ":" cookie-info)
                  (if (not= status 200)
                    resp
                    (let [cookie-tuple [authn-info/authn-cookie cookie]]
                      (if redirect-url
                        (r/response-final-redirect redirect-url cookie-tuple)
                        (r/response-created session-id cookie-tuple)))))
                (gu/throw-no-matched-user redirect-url))))
          (gu/throw-no-user-info redirect-url))
        (gu/throw-no-access-token redirect-url))
      (gu/throw-missing-oauth-code redirect-url))))


(defmethod callback/execute action-name
  [{callback-id :id {session-id :href} :target-resource :as callback-resource} request]
  (try
    (if-let [resp (validate-session request session-id)]
      resp
      (do
        (utils/callback-failed! callback-id)
        (r/map-response "could not validate github session" 400)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))

