(ns com.sixsq.nuvla.server.resources.session-github
  (:require
    [com.sixsq.nuvla.auth.utils.timestamp :as ts]
    [com.sixsq.nuvla.server.resources.callback-create-session-github :as cb]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.github.utils :as gu]
    [com.sixsq.nuvla.server.resources.session :as p]
    [com.sixsq.nuvla.server.resources.session.utils :as sutils]
    [com.sixsq.nuvla.server.resources.spec.session :as session]
    [com.sixsq.nuvla.server.resources.spec.session-template-github :as st-github]))


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
  [{:keys [href instance redirect-url] :as _resource} {:keys [headers base-uri] :as _request}]
  (let [[client-id client-secret] (gu/config-github-params redirect-url instance)]
    (if (and client-id client-secret)
      ;; fake session values will be replaced after callback execution
      (let [session      (sutils/create-session nil "user-id" {:href href} headers authn-method redirect-url)
            session      (assoc session :expiry (ts/rfc822->iso8601 (ts/expiry-later-rfc822 login-request-timeout)))
            callback-url (sutils/create-callback base-uri (:id session) cb/action-name)
            redirect-url (format gu/github-oath-endpoint client-id callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} session])
      (gu/throw-bad-client-config authn-method redirect-url))))


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
