(ns com.sixsq.nuvla.server.resources.session-mitreid-token
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.cookies :as cookies]
    [com.sixsq.nuvla.auth.external :as ex]
    [com.sixsq.nuvla.auth.utils.sign :as sign]
    [com.sixsq.nuvla.auth.utils.timestamp :as ts]
    [com.sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.session :as p]
    [com.sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.nuvla.server.resources.session.utils :as sutils]
    [com.sixsq.nuvla.server.resources.spec.session :as session]
    [com.sixsq.nuvla.server.resources.spec.session-template-mitreid-token :as st-mitreid-token]
    [com.sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


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
  [{:keys [token instance href redirect-url] :as _resource} {:keys [headers] :as _request}]
  (if token
    (let [{:keys [client-ips]} (oidc-utils/config-mitreid-token-params redirect-url instance)
          {:keys [public-key]} (oidc-utils/config-mitreid-params redirect-url instance)]
      (try
        (let [{:keys [sub] :as claims} (sign/unsign-cookie-info token public-key)
              roles (concat (oidc-utils/extract-roles claims)
                            (oidc-utils/extract-groups claims)
                            (oidc-utils/extract-entitlements claims))]
          (log/debug "MITREid token authentication claims for" instance ":" (pr-str claims))
          (if sub
            (if-let [matched-user-id (uiu/user-identifier->user-id :mitreid instance sub)]
              (let [{identifier :name} (ex/get-user matched-user-id)
                    {:keys [id client-ip] :as session} (sutils/create-session
                                                         nil matched-user-id {:href href}
                                                         headers authn-method redirect-url)
                    cookie-info  (cookies/create-cookie-info matched-user-id
                                                             :session-id id
                                                             :roles-ext roles)
                    cookie       (cookies/create-cookie cookie-info)
                    expires      (ts/rfc822->iso8601 (:expires cookie))
                    claims-roles (:roles cookie-info)
                    session      (cond-> (assoc session :expiry expires
                                                        :identifier (or identifier matched-user-id))
                                         claims-roles (assoc :roles claims-roles))]

                ;; only validate the client IP address, if the parameter is set
                (when client-ips
                  (when-not ((set client-ips) client-ip)
                    (oidc-utils/throw-invalid-address client-ip redirect-url)))

                (log/debug "MITREid cookie token claims for" (u/id->uuid href) ":" (pr-str cookie-info))
                (let [cookies {authn-info/authn-cookie cookie}]
                  (if redirect-url
                    [{:status 303, :headers {"Location" redirect-url}, :cookies cookies} session]
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
  (std-crud/initialize p/resource-type ::session/session))
