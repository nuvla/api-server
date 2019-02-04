(ns com.sixsq.slipstream.ssclj.resources.user-mitreid
  (:require
    [com.sixsq.slipstream.ssclj.resources.callback :as callback]
    [com.sixsq.slipstream.ssclj.resources.callback-create-user-mitreid :as user-mitreid-callback]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.slipstream.ssclj.resources.spec.user]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-mitreid :as ut-mitreid]
    [com.sixsq.slipstream.ssclj.resources.user :as p]
    [com.sixsq.slipstream.ssclj.resources.user-template-mitreid :as user-template]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-mitreid/schema-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [create-document]
  (create-validate-fn create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(def create-user-mitreid-callback (partial callback/create user-mitreid-callback/action-name))


(defmethod p/tpl->user user-template/registration-method
  [{:keys [href instance redirectURI] :as resource} {:keys [headers base-uri] :as request}]
  (let [{:keys [clientID authorizeURL]} (oidc-utils/config-mitreid-params redirectURI instance)
        data (when redirectURI {:redirectURI redirectURI})
        callback-url (create-user-mitreid-callback base-uri href data)
        redirect-url (oidc-utils/create-redirect-url authorizeURL clientID callback-url)]
    [{:status 303, :headers {"Location" redirect-url}} nil]))
