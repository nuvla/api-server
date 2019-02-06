(ns sixsq.nuvla.server.resources.user-mitreid
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-create-user-mitreid :as user-mitreid-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-mitreid :as ut-mitreid]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-mitreid :as user-template]))

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
