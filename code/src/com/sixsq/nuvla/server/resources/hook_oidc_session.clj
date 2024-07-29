(ns com.sixsq.nuvla.server.resources.hook-oidc-session
  "
Stripe oidc session.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.cookies :as cookies]
    [com.sixsq.nuvla.auth.external :as ex]
    [com.sixsq.nuvla.auth.oidc :as auth-oidc]
    [com.sixsq.nuvla.auth.utils.http :as uh]
    [com.sixsq.nuvla.auth.utils.sign :as sign]
    [com.sixsq.nuvla.auth.utils.timestamp :as ts]
    [com.sixsq.nuvla.server.app.params :as app-params]
    [com.sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.nuvla.server.resources.session.utils :as sutils]
    [com.sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const action "oidc-session")

(defn validate-session
  [{:keys [base-uri params cookies] :as request} redirect-ui-url]
  (let [session-id        (-> cookies
                              (get authn-info/future-session-cookie)
                              :value)
        instance          (get params :instance oidc-utils/geant-instance)
        redirect-hook-url (cond-> (str base-uri "hook" "/" action)
                                  (not= instance oidc-utils/geant-instance) (str "/" instance))
        {:keys [client-id client-secret token-url jwks-url]} (oidc-utils/config-oidc-params redirect-ui-url instance)]
    (log/info "hook-oidc-session redirect request:" request)
    (if-let [code (uh/param-value request :code)]
      (if-let [id-token (auth-oidc/get-id-token client-id client-secret token-url
                                                code redirect-hook-url)]
        (try
          (let [public-key (auth-oidc/get-public-key id-token jwks-url)
                {:keys [sub] :as claims} (sign/unsign-cookie-info id-token public-key)
                roles (concat (oidc-utils/extract-roles claims)
                              (oidc-utils/extract-groups claims)
                              (oidc-utils/extract-entitlements claims))]
            (log/debug "OIDC access token claims for" instance ":" (pr-str claims))
            (if sub
              (if-let [matched-user-id (uiu/user-identifier->user-id :oidc instance sub)]
                (let [{identifier :name} (ex/get-user matched-user-id)
                      {session-id :id
                       :as        current-session} (crud/retrieve-by-id-as-admin session-id)
                      cookie-info     (cookies/create-cookie-info matched-user-id
                                                                  :session-id session-id
                                                                  :roles-ext roles)
                      cookie          (cookies/create-cookie cookie-info)
                      expires         (ts/rfc822->iso8601 (:expires cookie))
                      claims          (:claims cookie-info)
                      updated-session (cond-> (assoc current-session
                                                :user matched-user-id
                                                :identifier (or identifier matched-user-id)
                                                :expiry expires)
                                              claims (assoc :roles claims))
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
                            (str/replace
                              (re-pattern (str app-params/service-context "$"))
                              app-params/ui-context)
                            (str "sign-in"))]
    (try
      (if-let [resp (validate-session request redirect-ui-url)]
        resp
        (r/map-response "could not validate OIDC session" 400))
      (catch Exception e
        (or (ex-data e) (throw e))))))
