(ns com.sixsq.nuvla.server.resources.user-mitreid
  (:require
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback-create-user-mitreid :as user-mitreid-callback]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.nuvla.server.resources.spec.user]
    [com.sixsq.nuvla.server.resources.spec.user-template-mitreid :as ut-mitreid]
    [com.sixsq.nuvla.server.resources.user-interface :as p]
    [com.sixsq.nuvla.server.resources.user-template-mitreid :as user-template]))

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
  [{:keys [href instance redirect-url] :as _resource} {:keys [base-uri] :as _request}]
  (let [{:keys [client-id authorize-url]} (oidc-utils/config-mitreid-params redirect-url instance)]
    (if (and client-id authorize-url)
      (let [data         (when redirect-url {:redirect-url redirect-url})
            callback-url (create-user-mitreid-callback base-uri href :data data)
            redirect-url (oidc-utils/create-redirect-url authorize-url client-id callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} nil])
      (oidc-utils/throw-bad-client-config user-template/registration-method redirect-url))))
