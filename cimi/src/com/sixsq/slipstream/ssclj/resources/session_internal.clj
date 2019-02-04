(ns com.sixsq.slipstream.ssclj.resources.session-internal
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.cookies :as cookies]
    [com.sixsq.slipstream.auth.internal :as auth-internal]
    [com.sixsq.slipstream.auth.utils.timestamp :as ts]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.session :as p]
    [com.sixsq.slipstream.ssclj.resources.session-template-internal :as tpl]
    [com.sixsq.slipstream.ssclj.resources.session.utils :as sutils]
    [com.sixsq.slipstream.ssclj.resources.spec.session :as session]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template-internal :as st-internal]
    [com.sixsq.slipstream.util.response :as r]))

(def ^:const authn-method "internal")

;;
;; schemas
;;

(def SessionDescription
  tpl/desc)

;;
;; description
;;
(def ^:const desc SessionDescription)

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
