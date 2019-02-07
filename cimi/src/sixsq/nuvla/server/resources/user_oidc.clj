(ns sixsq.nuvla.server.resources.user-oidc
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-create-user-oidc :as user-oidc-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.spec.user-template-oidc :as ut-oidc]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-oidc :as user-template]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-oidc/schema-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [{resource :template :as create-document}]
  (create-validate-fn create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(def create-user-oidc-callback (partial callback/create user-oidc-callback/action-name))


(defmethod p/tpl->user user-template/registration-method
  [{:keys [href instance redirectURI] :as resource} {:keys [base-uri] :as request}]
  (let [{:keys [clientID authorizeURL]} (oidc-utils/config-oidc-params redirectURI instance)
        data (when redirectURI {:redirectURI redirectURI})
        callback-url (create-user-oidc-callback base-uri href data)
        redirect-url (oidc-utils/create-redirect-url authorizeURL clientID callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} nil]))
