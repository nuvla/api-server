(ns sixsq.nuvla.server.resources.session-mitreid
  (:require
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.resources.callback-create-session-mitreid :as cb]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-mitreid :as st-mitreid]))


(def ^:const authn-method "mitreid")


(def ^:const login-request-timeout (* 3 60))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))


(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::st-mitreid/schema-create))


(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into session resource
;;

(defmethod p/tpl->session authn-method
  [{:keys [href instance redirectURI] :as resource} {:keys [headers base-uri] :as request}]
  (let [{:keys [clientID authorizeURL]} (oidc-utils/config-mitreid-params redirectURI instance)
        session-init (cond-> {:href href}
                             redirectURI (assoc :redirectURI redirectURI))
        session (sutils/create-session session-init headers authn-method)
        session (assoc session :expiry (ts/rfc822->iso8601 (ts/expiry-later-rfc822 login-request-timeout)))
        callback-url (oidc-utils/create-callback base-uri (:id session) cb/action-name)
        redirect-url (oidc-utils/create-redirect-url authorizeURL clientID callback-url)]
    [{:status 303, :headers {"Location" redirect-url}} session]))

;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
