(ns sixsq.nuvla.server.resources.user-email-invitation
  "
Contains the functions necessary to create a user resource from an invitation
using an email address.
"
  (:require
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.server.resources.callback-user-password-set :as callback-pass-set]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.spec.user-template-email-invitation :as spec-email-invitation]
    [sixsq.nuvla.server.resources.user-interface :as p]
    [sixsq.nuvla.server.resources.user-template-email-invitation :as email-invitation]
    [sixsq.nuvla.server.resources.user.password :as password-utils]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.general :as gen-util]))


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
  [resource _request]
  [nil (password-utils/create-user-map resource)])


;;
;; create/update all related resources
;;


;;
;; creates email validation callback after user is created
;; logs and then ignores any exceptions when creating callback
;;


(defmethod p/post-user-add email-invitation/registration-method
  [{:keys [id redirect-url] :as _resource} {:keys [base-uri body] :as request}]
  (try
    (let [{{:keys [email]} :template} body
          invited-by   (auth-password/invited-by request)
          callback-url (callback-pass-set/create-callback
                         base-uri id :expires (u/ttl->timestamp 2592000))] ;;30 days
      (user-utils/create-user-subresources id :email email)

      (-> (str redirect-url "?callback=" (gen-util/encode-uri-component callback-url)
               "&type=" (gen-util/encode-uri-component "invitation")
               "&username=" (gen-util/encode-uri-component email))
          (email-utils/send-invitation-email email invited-by)))
    (catch Exception e
      (user-utils/delete-user id)
      (throw e))))
