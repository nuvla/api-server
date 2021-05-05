(ns sixsq.nuvla.server.resources.hook-oidc-user
  "
Stripe oidc user.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.oidc :as auth-oidc]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as app-params]
    [sixsq.nuvla.server.resources.hook-oidc-session :refer [instance]]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action "oidc-user")

(defn register-user
  [{:keys [base-uri] :as request} redirect-ui-url]
  (let [{:keys [client-id client-secret
                public-key token-url]} (oidc-utils/config-oidc-params redirect-ui-url instance)
        redirect-hook-url (str base-uri "hook" "/" action)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-oidc/get-access-token
                              client-id client-secret token-url code redirect-hook-url)]
        (try
          (let [{:keys [sub email] :as claims} (sign/unsign-cookie-info access-token public-key)]
            (log/debugf "oidc access token claims for %s: %s" instance (pr-str claims))
            (if sub
              (or
                (ex/create-user! :oidc {:instance       instance
                                        :external-id    sub
                                        :external-email (or email (str sub "@fake-email.com"))})
                (oidc-utils/throw-user-exists redirect-ui-url))
              (oidc-utils/throw-no-subject redirect-ui-url)))
          (catch Exception e
            (oidc-utils/throw-invalid-access-code (str e) redirect-ui-url)))
        (oidc-utils/throw-no-access-token redirect-ui-url))
      (oidc-utils/throw-missing-code redirect-ui-url))))


(defn execute
  [{:keys [base-uri] :as request}]
  (log/debug "Executing hook" action request)
  (let [redirect-ui-url         (-> base-uri
                                    (str/replace
                                      (re-pattern (str app-params/service-context "$"))
                                      app-params/ui-context)
                                    (str "sign-up"))
        redirect-ui-url-success (str redirect-ui-url "?message=signup-validation-success")]
    (try
      (if-let [user-id (register-user request redirect-ui-url)]
        (r/map-response (format "'%s' created" user-id) 303 nil redirect-ui-url-success)
        (r/map-response "could not create OIDC user" 400))
      (catch Exception e
        (or (ex-data e) (throw e))))))
