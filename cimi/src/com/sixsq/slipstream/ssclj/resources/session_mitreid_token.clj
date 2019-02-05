(ns com.sixsq.slipstream.ssclj.resources.session-mitreid-token
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.cookies :as cookies]
    [com.sixsq.slipstream.auth.external :as ex]
    [com.sixsq.slipstream.auth.internal :as auth-internal]
    [com.sixsq.slipstream.auth.utils.sign :as sign]
    [com.sixsq.slipstream.auth.utils.timestamp :as ts]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.session :as p]
    [com.sixsq.slipstream.ssclj.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.slipstream.ssclj.resources.session.utils :as sutils]
    [com.sixsq.slipstream.ssclj.resources.spec.session :as session]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template-mitreid-token :as st-mitreid-token]))


(def ^:const authn-method "mitreid-token")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::st-mitreid-token/schema-create))
(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into session resource
;;

(defmethod p/tpl->session authn-method
  [{:keys [token instance href redirectURI] :as resource} {:keys [headers] :as request}]
  (if token
    (let [{:keys [clientIPs]} (oidc-utils/config-mitreid-token-params redirectURI instance)
          {:keys [publicKey]} (oidc-utils/config-mitreid-params redirectURI instance)]
      (try
        (let [{:keys [sub] :as claims} (sign/unsign-claims token publicKey)
              roles (concat (oidc-utils/extract-roles claims)
                            (oidc-utils/extract-groups claims)
                            (oidc-utils/extract-entitlements claims))]
          (log/debug "MITREid token authentication claims for" instance ":" (pr-str claims))
          (if sub
            (if-let [matched-user (ex/match-oidc-username :mitreid sub instance)]
              (let [session-info {:href href, :username matched-user, :redirectURI redirectURI}
                    {:keys [id clientIP] :as session} (sutils/create-session session-info headers authn-method)
                    claims (cond-> (auth-internal/create-claims matched-user)
                                   id (assoc :session id)
                                   id (update :roles #(str id " " %))
                                   roles (update :roles #(str % " " (str/join " " roles))))
                    cookie (cookies/claims-cookie claims)
                    expires (ts/rfc822->iso8601 (:expires cookie))
                    claims-roles (:roles claims)
                    session (cond-> (assoc session :expiry expires)
                                    claims-roles (assoc :roles claims-roles))]

                ;; only validate the client IP address, if the parameter is set
                (when clientIPs
                  (when-not ((set clientIPs) clientIP)
                    (oidc-utils/throw-invalid-address clientIP redirectURI)))

                (log/debug "MITREid cookie token claims for" (u/document-id href) ":" (pr-str claims))
                (let [cookies {(sutils/cookie-name (:id session)) cookie}]
                  (if redirectURI
                    [{:status 303, :headers {"Location" redirectURI}, :cookies cookies} session]
                    [{:cookies cookies} session])))
              (oidc-utils/throw-inactive-user sub nil))
            (oidc-utils/throw-no-subject nil)))
        (catch Exception e
          (oidc-utils/throw-invalid-access-code (str e) nil))))
    (oidc-utils/throw-no-access-token nil)))


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-url ::session/session))
