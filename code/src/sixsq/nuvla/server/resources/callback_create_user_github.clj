(ns sixsq.nuvla.server.resources.callback-create-user-github
  "Creates a new GitHub user resource after external authentication has succeeded."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.github :as auth-github]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.github.utils :as gu]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "user-github-creation")


(defn register-user
  [{{:keys [href]} :target-resource {:keys [redirect-url]} :data :as callback-resource} request]
  (let [{:keys [instance]} (crud/retrieve-by-id-as-admin href)
        [client-id client-secret] (gu/config-github-params redirect-url instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-github/get-github-access-token client-id client-secret code)]
        (if-let [user-info (auth-github/get-github-user-info access-token)]
          (do
            (log/debugf "github user info for %s: %s" instance (str user-info))
            (let [github-login (:login user-info)
                  github-email (auth-github/retrieve-email user-info access-token)]
              (if github-login
                (or (ex/create-user! :github {:external-login github-login
                                              :external-email github-email})
                    (gu/throw-user-exists github-login redirect-url))
                (gu/throw-no-matched-user redirect-url))))
          (gu/throw-no-user-info redirect-url))
        (gu/throw-no-access-token redirect-url))
      (gu/throw-missing-oauth-code redirect-url))))


(defmethod callback/execute action-name
  [{callback-id :id {:keys [redirect-url]} :data :as callback-resource} request]
  (log/debug "Executing callback" callback-id)
  (try
    (if-let [username (register-user callback-resource request)]
      (let [msg (format "user '%s' created" username)]
        (utils/callback-succeeded! callback-id)
        (if redirect-url
          (r/map-response msg 303 callback-id redirect-url)
          (r/map-response msg 201)))
      (do
        (utils/callback-failed! callback-id)
        (r/map-response "could not create github user" 400)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))
