(ns com.sixsq.nuvla.server.resources.session-password
  "
Provides the functions necessary to create a session from a username and
password.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.cookies :as cookies]
    [com.sixsq.nuvla.auth.password :as auth-password]
    [com.sixsq.nuvla.auth.utils.timestamp :as ts]
    [com.sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [com.sixsq.nuvla.server.resources.callback-create-session-2fa :as callback-2fa]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.session :as p]
    [com.sixsq.nuvla.server.resources.session.utils :as sutils]
    [com.sixsq.nuvla.server.resources.spec.session :as session]
    [com.sixsq.nuvla.server.resources.spec.session-template-password :as st-password]
    [com.sixsq.nuvla.server.resources.two-factor-auth.utils :as auth-2fa]
    [com.sixsq.nuvla.server.util.response :as r]))


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


(defn create-session-password-for-user-2fa
  [{:keys [href username] :as _resource}
   {:keys [base-uri headers body] :as _request}
   {user-id :id method :auth-method-2fa :as user}]
  (let [redirect-url (-> body :template :redirect-url)
        ;; fake session values will be replaced after callback execution
        session      (-> (sutils/create-session
                           username user-id {:href href} headers authn-method
                           redirect-url)
                         (assoc :expiry (ts/rfc822->iso8601
                                          (ts/expiry-later-rfc822 120))))
        token        (auth-2fa/generate-token method user)
        session-id   (:id session)
        callback-url (callback-2fa/create-callback
                       base-uri session-id :data
                       (cond-> {:method  method
                                :headers headers}
                               token (assoc :token token))
                       :expires (u/ttl->timestamp 120)
                       :tries-left 3)]
    (auth-2fa/send-token method user token)
    [(r/map-response "Authorization code requested"
                     200 session-id callback-url) session]))

(defn create-session-password-for-user
  [{:keys [href username] :as _resource} {:keys [headers] :as _request} user]
  (let [user-id     (:id user)
        session     (sutils/create-session
                      username user-id {:href href} headers authn-method)
        cookie-info (cookies/create-cookie-info user-id
                                                :session-id (:id session)
                                                :headers headers
                                                :client-ip (:client-ip session))
        cookie      (cookies/create-cookie cookie-info)
        expires     (ts/rfc822->iso8601 (:expires cookie))
        claims      (:claims cookie-info)
        session     (cond-> (assoc session :expiry expires)
                            claims (assoc :roles claims))
        cookies     {authn-info/authn-cookie cookie}]
    (log/debug "password cookie token claims for"
               (u/id->uuid href) ":" cookie-info)
    [{:cookies cookies} session]))


(defmethod p/tpl->session authn-method
  [{:keys [username password] :as resource} request]
  (if-let [user (auth-password/valid-user-password username password)]
    (if (#{auth-2fa/method-totp auth-2fa/method-email} (:auth-method-2fa user))
      (create-session-password-for-user-2fa resource request user)
      (create-session-password-for-user resource request user))
    (throw (r/ex-unauthorized username))))


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
