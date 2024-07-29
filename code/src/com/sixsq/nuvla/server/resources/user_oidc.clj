(ns com.sixsq.nuvla.server.resources.user-oidc
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.hook :as hook]
    [com.sixsq.nuvla.server.resources.hook-oidc-user :as hook-oidc-user]
    [com.sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.nuvla.server.resources.spec.user-template-oidc :as ut-oidc]
    [com.sixsq.nuvla.server.resources.user-interface :as p]
    [com.sixsq.nuvla.server.resources.user-template-oidc :as user-template]))


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

(defn get-authorize-url
  [{:keys [instance redirect-url] :as _resource} {:keys [base-uri] :as _request}]
  (let [{:keys [client-id authorize-url]} (oidc-utils/config-oidc-params redirect-url instance)
        redirect-url (cond-> (str base-uri hook/resource-type "/" hook-oidc-user/action)
                             (not= instance oidc-utils/geant-instance) (str "/" instance))]
    (oidc-utils/create-redirect-url authorize-url client-id redirect-url "openid email")))


(defmethod p/tpl->user user-template/registration-method
  [resource request]
  [{:status 303, :headers {"Location" (get-authorize-url resource request)}} nil])
