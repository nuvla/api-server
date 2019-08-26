(ns sixsq.nuvla.server.resources.user-minimum
  "
Provides the functions necessary to create a `user` resource from minimal
information."
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-minimum :as spec-minimum]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [sixsq.nuvla.server.resources.user.password :as password-utils]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))


;;
;; multimethod for validation
;;

(def create-validate-fn-username (u/create-spec-validation-fn ::spec-minimum/schema-create))


(defmethod p/create-validate-subtype minimum/registration-method
  [create-document]
  (create-validate-fn-username create-document))


;;
;; transformation of template
;;

(defmethod p/tpl->user minimum/registration-method
  [resource request]
  [nil (-> resource
           password-utils/create-user-map
           (assoc :state "ACTIVE"))])


;;
;; create/update all related resources
;;

(defmethod p/post-user-add minimum/registration-method
  [{user-id :id :as resource} {:keys [body] :as request}]
  (try
    (let [{{:keys [username email]} :template} body]
      (when username
        (user-utils/create-identifier user-id username))
      (when email
        (user-utils/create-identifier user-id email)))
    (catch Exception e
      (user-utils/delete-user user-id)
      (throw e))))
