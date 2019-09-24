(ns sixsq.nuvla.server.resources.user-email-invitation
  "
Contains the functions necessary to create a user resource from an invitation
using an email address.
"
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.spec.user-template-email-invitation :as spec-email-invitation]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-email-invitation :as email-invitation]
    [sixsq.nuvla.server.resources.user.password :as password-utils]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))


;;
;; multimethod for validation
;;

(def create-validate-fn-email (u/create-spec-validation-fn ::spec-email-invitation/schema-create))


(defmethod p/create-validate-subtype email-invitation/registration-method
  [create-document]
  (create-validate-fn-email create-document))


;;
;; transformation of template
;;

(defmethod p/tpl->user email-invitation/registration-method
  [resource request]
  [nil (password-utils/create-user-map resource)])


;;
;; create/update all related resources
;;


;;
;; creates email validation callback after user is created
;; logs and then ignores any exceptions when creating callback
;;

(def create-user-email-callback (partial callback/create user-email-callback/action-name))


(defmethod p/post-user-add email-invitation/registration-method
  [{:keys [id redirect-url] :as resource} {:keys [base-uri body nuvla/authn] :as request}]
  (try
    (let [{{:keys [email]} :template} body
          callback-data      {:redirect-url redirect-url}
          invited-by-user-id (:user-id authn)
          invited-by         (try
                               (crud/retrieve-by-id-as-admin invited-by-user-id)
                               (catch Exception _
                                 invited-by-user-id))]
      (user-utils/create-user-subresources id email nil nil)

      (-> (create-user-email-callback base-uri id :data callback-data)
          (email-utils/send-invitation-email email invited-by)))
    (catch Exception e
      (user-utils/delete-user id)
      (throw e))))
