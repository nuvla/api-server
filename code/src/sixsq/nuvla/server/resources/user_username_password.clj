(ns sixsq.nuvla.server.resources.user-username-password
  "
Provides the functions necessary to create a `user` resource from a username,
password, and other given information."
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-username-password :as spec-username-password]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [sixsq.nuvla.server.resources.user.password :as password-utils]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))


;;
;; multimethod for validation
;;

(def create-validate-fn-username (u/create-spec-validation-fn ::spec-username-password/schema-create))


(defmethod p/create-validate-subtype username-password/registration-method
  [{resource :template :as create-document}]
  (user-utils/check-password-constraints resource)
  (create-validate-fn-username create-document))


;;
;; transformation of template
;;

(defmethod p/tpl->user username-password/registration-method
  [resource request]
  (-> (password-utils/create-user-map resource)
      (assoc :state "ACTIVE")))


;;
;; create/update all related resources
;;

(defmethod p/post-user-add username-password/registration-method
  [{user-id :id :as resource} {:keys [body] :as request}]
  (try
    (let [{{:keys [password username]} :template} body]
      (user-utils/create-user-subresources user-id nil password username))
    (catch Exception e
      (user-utils/delete-user user-id)
      (throw e))))
