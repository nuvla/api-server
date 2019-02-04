(ns com.sixsq.slipstream.ssclj.resources.user-oidc
  (:require
    [com.sixsq.slipstream.ssclj.resources.callback :as callback]
    [com.sixsq.slipstream.ssclj.resources.callback-create-user-oidc :as user-oidc-callback]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-oidc :as ut-oidc]
    [com.sixsq.slipstream.ssclj.resources.user :as p]
    [com.sixsq.slipstream.ssclj.resources.user-template-oidc :as user-template]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-oidc/schema-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [{resource :userTemplate :as create-document}]
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
