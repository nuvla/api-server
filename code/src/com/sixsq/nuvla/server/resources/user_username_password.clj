(ns com.sixsq.nuvla.server.resources.user-username-password
  "
Provides the functions necessary to create a `user` resource from a username,
password, and other given information."
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.spec.user]
    [com.sixsq.nuvla.server.resources.spec.user-template-username-password :as spec-username-password]
    [com.sixsq.nuvla.server.resources.user-interface :as p]
    [com.sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [com.sixsq.nuvla.server.resources.user.password :as password-utils]
    [com.sixsq.nuvla.server.resources.user.utils :as user-utils]))


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
  [resource _request]
  [nil (-> resource
           password-utils/create-user-map
           (assoc :state "ACTIVE"))])


;;
;; create/update all related resources
;;

(defmethod p/post-user-add username-password/registration-method
  [{user-id :id :as _resource} {:keys [body] :as _request}]
  (try
    (let [{{:keys [password username]} :template} body]
      (user-utils/create-user-subresources user-id
                                           :password password
                                           :username username))
    (catch Exception e
      (user-utils/delete-user user-id)
      (throw e))))
