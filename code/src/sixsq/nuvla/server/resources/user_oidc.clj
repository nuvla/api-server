(ns sixsq.nuvla.server.resources.user-oidc
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-create-user-oidc :as user-oidc-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.hook :as hook]
    [sixsq.nuvla.server.resources.hook-oidc-user :as hook-oidc-user]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.spec.user-template-oidc :as ut-oidc]
    [sixsq.nuvla.server.resources.user-interface :as p]
    [sixsq.nuvla.server.resources.user-template-oidc :as user-template]))


;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-oidc/schema-create))


(defmethod p/create-validate-subtype user-template/registration-method
  [create-document]
  (create-validate-fn create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(def create-user-oidc-callback (partial callback/create user-oidc-callback/action-name))

(defn get-authorize-url
  [{:keys [href instance redirect-url] :as _resource} {:keys [base-uri] :as _request}]
  (let [{:keys [client-id authorize-url
                redirect-url-resource]} (oidc-utils/config-oidc-params redirect-url instance)
        redirect-url (if (= redirect-url-resource "callback")
                       (create-user-oidc-callback
                         base-uri href :data (when redirect-url {:redirect-url redirect-url}))
                       (cond-> (str base-uri hook/resource-type "/" hook-oidc-user/action)
                         (not= instance oidc-utils/geant-instance) (str "/" instance)))]
    (oidc-utils/create-redirect-url authorize-url client-id redirect-url "openid email")))


(defmethod p/tpl->user user-template/registration-method
  [resource request]
  [{:status 303, :headers {"Location" (get-authorize-url resource request)}} nil])
