(ns sixsq.nuvla.server.resources.user-password
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-email-password :as spec-email-password]
    [sixsq.nuvla.server.resources.spec.user-template-username-password :as spec-username-password]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))


;;
;; multimethods for validation
;;

;; FIXME: These should probably be in user-template resources rather than here.

(def create-validate-fn-email (u/create-spec-validation-fn ::spec-email-password/schema-create))


(defmethod p/create-validate-subtype email-password/registration-method
  [{resource :template :as create-document}]
  (user-utils/check-password-constraints resource)
  (create-validate-fn-email create-document))


(def create-validate-fn-username (u/create-spec-validation-fn ::spec-username-password/schema-create))


(defmethod p/create-validate-subtype username-password/registration-method
  [{resource :template :as create-document}]
  (user-utils/check-password-constraints resource)
  (create-validate-fn-username create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(defn create-user-map
  [{:keys [name description tags method] :as resource}]
  (cond-> {:resource-type p/resource-type
           :method        method
           :state         "NEW"}
          name (assoc :name name)
          description (assoc :description description)
          tags (assoc :tags tags)))


(defmethod p/tpl->user email-password/registration-method
  [{:keys [redirectURI] :as resource} request]
  (let [user-map (create-user-map resource)]
    (if redirectURI
      [{:status 303, :headers {"Location" redirectURI}} user-map]
      [nil user-map])))


;; FIXME: Should be in separate namespace.
(defmethod p/tpl->user username-password/registration-method
  [{:keys [redirectURI] :as resource} request]
  (let [user-map (-> (create-user-map resource)
                     (assoc :state "ACTIVE"))]
    (if redirectURI
      [{:status 303, :headers {"Location" redirectURI}} user-map]
      [nil user-map])))


;;
;; creates email validation callback after user is created
;; logs and then ignores any exceptions when creating callback
;;

(def create-user-email-callback (partial callback/create user-email-callback/action-name))


(defmethod p/post-user-add email-password/registration-method
  [{user-id :id :as resource} {:keys [base-uri body] :as request}]
  (try
    (let [{{:keys [email password username]} :template} body]
      (user-utils/create-user-subresources user-id email password username)
      (-> (create-user-email-callback base-uri user-id)
          (email-utils/send-validation-email email)))
    (catch Exception e
      (user-utils/delete-user user-id)
      (throw e))))

;; FIXME: Should be in separate namespace.
(defmethod p/post-user-add username-password/registration-method
  [{user-id :id :as resource} {:keys [base-uri body] :as request}]
  (try
    (let [{{:keys [password username]} :template} body]
      (user-utils/create-user-subresources user-id nil password username))
    (catch Exception e
      (user-utils/delete-user user-id)
      (throw e))))
