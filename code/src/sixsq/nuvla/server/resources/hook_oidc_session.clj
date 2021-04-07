(ns sixsq.nuvla.server.resources.hook-oidc-session
  "
Stripe oidc session.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.oidc :as auth-oidc]
    [sixsq.nuvla.auth.utils.http :as uh]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.app.params :as app-params]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action "oidc-session")

;; hardcoded instance
(def ^:const instance "geant")

(defn validate-session
  [{:keys [base-uri headers] :as request} redirect-ui-url]
  (let [redirect-hook-url (str base-uri "hook" "/" action)
        {:keys [client-id client-secret
                public-key token-url]} (oidc-utils/config-oidc-params redirect-ui-url instance)]
    (if-let [code (uh/param-value request :code)]
      (if-let [access-token (auth-oidc/get-access-token client-id client-secret token-url
                                                        code redirect-hook-url)]
        (try
          (let [{:keys [sub] :as claims} (sign/unsign-cookie-info access-token public-key)
                roles (concat (oidc-utils/extract-roles claims)
                              (oidc-utils/extract-groups claims)
                              (oidc-utils/extract-entitlements claims))]
            (log/debug "OIDC access token claims for" instance ":" (pr-str claims))
            (if sub
              (if-let [matched-user-id (uiu/user-identifier->user-id :oidc instance sub)]
                (let [{identifier :name} (ex/get-user matched-user-id)
                      {session-id :id
                       :as        session} (-> (sutils/create-session
                                                 nil "user-id" {:href "session-template/oidc-geant"}
                                                 headers "oidc" redirect-ui-url)
                                               (assoc :expiry
                                                      (ts/rfc822->iso8601
                                                        (ts/expiry-later-rfc822 (* 3 60)))))
                      cookie-info     (cookies/create-cookie-info matched-user-id
                                                                  :session-id session-id
                                                                  :roles-ext roles)
                      cookie          (cookies/create-cookie cookie-info)
                      expires         (ts/rfc822->iso8601 (:expires cookie))
                      claims          (:claims cookie-info)
                      groups          (:groups cookie-info)
                      updated-session (cond-> (assoc session
                                                :user matched-user-id
                                                :identifier (or identifier matched-user-id)
                                                :expiry expires)
                                              claims (assoc :roles claims)
                                              groups (assoc :groups groups))
                      {:keys [status] :as resp} (sutils/update-session session-id updated-session)]
                  (log/debug "OIDC cookie token claims for" instance ":" (pr-str cookie-info))
                  (if (not= status 200)
                    resp
                    (let [cookie-tuple [authn-info/authn-cookie cookie]]
                      (if redirect-ui-url
                        (r/response-final-redirect redirect-ui-url cookie-tuple)
                        (r/response-created session-id cookie-tuple)))))
                (oidc-utils/throw-inactive-user sub redirect-ui-url))
              (oidc-utils/throw-no-subject redirect-ui-url)))
          (catch Exception e
            (oidc-utils/throw-invalid-access-code (str e) redirect-ui-url)))
        (oidc-utils/throw-no-access-token redirect-ui-url))
      (oidc-utils/throw-missing-code redirect-ui-url))))


(defn execute
  [{:keys [base-uri] :as request}]
  (log/debug "Executing hook" action request)
  (let [redirect-ui-url (-> base-uri
                            (clojure.string/replace
                              (re-pattern (str app-params/service-context "$"))
                              app-params/ui-context)
                            (str "sign-in"))]
    (try
      (if-let [resp (validate-session request redirect-ui-url)]
        resp
        (r/map-response "could not validate OIDC session" 400))
      (catch Exception e
        (or (ex-data e) (throw e))))))
