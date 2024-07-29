(ns com.sixsq.nuvla.server.resources.user-minimum
  "
Provides the functions necessary to create a `user` resource from minimal
information."
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.spec.user]
    [com.sixsq.nuvla.server.resources.spec.user-template-minimum :as spec-minimum]
    [com.sixsq.nuvla.server.resources.user-interface :as p]
    [com.sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [com.sixsq.nuvla.server.resources.user.password :as password-utils]
    [com.sixsq.nuvla.server.resources.user.utils :as user-utils]))


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
  [resource _request]
  [nil (-> resource
           password-utils/create-user-map
           (assoc :state "ACTIVE"))])


;;
;; create/update all related resources
;;

(defmethod p/post-user-add minimum/registration-method
  [{user-id :id :as _resource} {:keys [body] :as _request}]
  (try
    (let [{{:keys [username email password]} :template} body]
      (user-utils/create-user-subresources user-id
                                           :username username
                                           :email email
                                           :email-validated true
                                           :password password))
    (catch Exception e
      (user-utils/delete-user user-id)
      (throw e))))
