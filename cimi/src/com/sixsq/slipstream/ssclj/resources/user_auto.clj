(ns com.sixsq.slipstream.ssclj.resources.user-auto
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.internal :as internal]
    [com.sixsq.slipstream.ssclj.resources.callback :as callback]
    [com.sixsq.slipstream.ssclj.resources.callback-user-email-validation :as user-email-callback]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.email.utils :as email-utils]
    [com.sixsq.slipstream.ssclj.resources.spec.user]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-self-registration :as ut-auto]
    [com.sixsq.slipstream.ssclj.resources.user :as p]
    [com.sixsq.slipstream.ssclj.resources.user-template-self-registration :as user-template]
    [com.sixsq.slipstream.ssclj.resources.user.utils :as user-utils]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-auto/schema-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [{resource :userTemplate :as create-document}]
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
