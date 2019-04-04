(ns sixsq.nuvla.server.resources.user-email-password
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.spec.user-template-email-password :as spec-email-password]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [sixsq.nuvla.server.resources.user.password :as password-utils]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))


;;
;; multimethod for validation
;;

(def create-validate-fn-email (u/create-spec-validation-fn ::spec-email-password/schema-create))


(defmethod p/create-validate-subtype email-password/registration-method
  [{resource :template :as create-document}]
  (user-utils/check-password-constraints resource)
  (create-validate-fn-email create-document))


;;
;; transformation of template
;;

(defmethod p/tpl->user email-password/registration-method
  [resource request]
  (password-utils/create-user-map resource))


;;
;; create/update all related resources
;;


;;
;; creates email validation callback after user is created
;; logs and then ignores any exceptions when creating callback
;;

(def create-user-email-callback (partial callback/create user-email-callback/action-name))


(defmethod p/post-user-add email-password/registration-method
  [{:keys [id redirect-url] :as resource} {:keys [base-uri body] :as request}]
  (try
    (let [{{:keys [email password username]} :template} body
          callback-data {:redirect-url redirect-url}]
      (user-utils/create-user-subresources id email password username)
      (-> (create-user-email-callback base-uri id callback-data)
          (email-utils/send-validation-email email)))
    (catch Exception e
      (user-utils/delete-user id)
      (throw e))))
