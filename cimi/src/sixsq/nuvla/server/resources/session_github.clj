(ns sixsq.nuvla.server.resources.session-github
  (:require
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.resources.callback-create-session-github :as cb]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.github.utils :as gu]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-github :as st-github]))

(def ^:const authn-method "github")

(def ^:const login-request-timeout (* 3 60))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::st-github/schema-create))
(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))

;;
;; transform template into session resource
;;

;; creates a temporary session and redirects to GitHub to start authentication workflow
(defmethod p/tpl->session authn-method
  [{:keys [href instance redirectURI] :as resource} {:keys [headers base-uri] :as request}]
  (let [[client-id client-secret] (gu/config-github-params redirectURI instance)]
    (if (and client-id client-secret)
      (let [session-init (cond-> {:href href}
                                 redirectURI (assoc :redirectURI redirectURI))
            session (sutils/create-session session-init headers authn-method)
            session (assoc session :expiry (ts/rfc822->iso8601 (ts/expiry-later-rfc822 login-request-timeout)))
            callback-url (sutils/create-callback base-uri (:id session) cb/action-name)
            redirect-url (format gu/github-oath-endpoint client-id callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} session])
      (gu/throw-bad-client-config authn-method redirectURI))))

;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-url ::session/session))
