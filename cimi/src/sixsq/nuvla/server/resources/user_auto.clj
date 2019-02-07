(ns sixsq.nuvla.server.resources.user-auto
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.internal :as internal]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-self-registration :as ut-auto]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-self-registration :as user-template]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-auto/schema-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [{resource :template :as create-document}]
  (user-utils/check-password-constraints resource)
  (create-validate-fn create-document))

;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(def user-defaults {:resourceURI p/resource-uri
                    :isSuperUser false
                    :deleted     false
                    :state       "NEW"})


(defn create-user-map
  [{:keys [password] :as resource}]
  (-> resource
      (merge user-defaults)
      (dissoc :passwordRepeat :instance :redirectURI
              :group :order :icon :hidden)
      (assoc :password (internal/hash-password password))))


(defmethod p/tpl->user user-template/registration-method
  [{:keys [redirectURI] :as resource} request]
  (let [user-map (create-user-map resource)]
    (if redirectURI
      [{:status 303, :headers {"Location" redirectURI}} user-map]
      [nil user-map])))


;;
;; creates email validation callback after user is created
;; logs and then ignores any exceptions when creating callback
;;

(def create-user-email-callback (partial callback/create user-email-callback/action-name))


(defmethod p/post-user-add user-template/registration-method
  [{:keys [id emailAddress] :as resource} {:keys [base-uri] :as request}]
  (try
    (-> (create-user-email-callback base-uri id)
        (email-utils/send-validation-email emailAddress))
    (catch Exception e
      (log/error (str e)))))
