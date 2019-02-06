(ns sixsq.nuvla.server.resources.session-internal
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.internal :as auth-internal]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-internal :as st-internal]
    [sixsq.nuvla.util.response :as r]))

(def ^:const authn-method "internal")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::st-internal/schema-create))
(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))

;;
;; transform template into session resource
;;

(defn create-claims [username headers session-id client-ip]
  (let [server (:slipstream-ssl-server-name headers)]
    (cond-> (auth-internal/create-claims username)
            server (assoc :server server)
            session-id (assoc :session session-id)
            session-id (update :roles #(str % " " session-id))
            client-ip (assoc :clientIP client-ip))))

(defmethod p/tpl->session authn-method
  [{:keys [href redirectURI] :as resource} {:keys [headers base-uri] :as request}]
  (let [{:keys [username] :as credentials} (select-keys resource #{:username :password})]
    (if (auth-internal/valid? credentials)
      (let [session (sutils/create-session (merge credentials {:href href}) headers authn-method)
            claims (create-claims username headers (:id session) (:clientIP session))
            cookie (cookies/claims-cookie claims)
            expires (ts/rfc822->iso8601 (:expires cookie))
            claims-roles (:roles claims)
            session (cond-> (assoc session :expiry expires)
                            claims-roles (assoc :roles claims-roles))]
        (log/debug "internal cookie token claims for" (u/document-id href) ":" claims)
        (let [cookies {(sutils/cookie-name (:id session)) cookie}]
          (if redirectURI
            [{:status 303, :headers {"Location" redirectURI}, :cookies cookies} session]
            [{:cookies cookies} session])))
      (if redirectURI
        (throw (r/ex-redirect (str "invalid credentials for '" username "'") nil redirectURI))
        (throw (r/ex-unauthorized username))))))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-url ::session/session))
