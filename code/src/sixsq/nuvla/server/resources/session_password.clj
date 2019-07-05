(ns sixsq.nuvla.server.resources.session-password
  "
Provides the functions necessary to create a session from a username and
password.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-password :as st-password]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const authn-method "password")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))

(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::st-password/schema-create))

(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))

;;
;; transform template into session resource
;;

(defn create-cookie-info [user headers session-id client-ip]
  (let [server (:nuvla-ssl-server-name headers)]
    (cond-> (auth-password/create-claims user)
            server (assoc :server server)
            session-id (assoc :session session-id)
            session-id (update :claims #(str % " " session-id))
            client-ip (assoc :client-ip client-ip))))



(defn create-session-password
  [username user headers href]
  (if user
    (let [user-id     (:id user)
          session     (sutils/create-session username user-id href headers authn-method)
          cookie-info (create-cookie-info user headers (:id session) (:client-ip session))
          cookie      (cookies/create-cookie cookie-info)
          expires     (ts/rfc822->iso8601 (:expires cookie))
          claims      (:claims cookie-info)
          session     (cond-> (assoc session :expiry expires)
                              claims (assoc :roles claims))
          cookies     {authn-info/authn-cookie cookie}]
      (log/debug "password cookie token claims for" (u/id->uuid href) ":" cookie-info)
      [{:cookies cookies} session])
    (throw (r/ex-unauthorized username))))


(defmethod p/tpl->session authn-method
  [{:keys [href username password] :as resource} {:keys [headers] :as request}]
  (let [user (auth-password/valid-user-password username password)]
    (create-session-password username user headers href)))


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
