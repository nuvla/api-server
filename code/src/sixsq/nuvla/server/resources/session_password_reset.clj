(ns sixsq.nuvla.server.resources.session-password-reset
  "
Provides the functions necessary to create a session from a password reset
request, while also resetting the password itself.
"
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-password-reset :as user-password-reset]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session-password :as session-password]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-password-reset :as st-password-reset]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const authn-method "password-reset")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::st-password-reset/schema-create))
(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))

;;
;; transform template into session resource
;;

;(crud/retrieve-by-id-as-admin href)


(def create-user-password-reset-callback (partial callback/create user-password-reset/action-name))


(defmethod p/tpl->session authn-method
  [{:keys [href username new-password redirect-url] :as resource} {:keys [base-uri headers] :as request}]

  (when-not (hashed-password/acceptable-password? new-password)
    (throw (r/ex-response hashed-password/acceptable-password-msg 400)))

  (let [{user-id       :id
         credential-id :credential-password
         email-id      :email :as user} (auth-password/active-user username)

        {email-address :address :as email} (when email-id
                                             (crud/retrieve-by-id-as-admin email-id))]

    (when-not email-address
      (throw (r/ex-response (str "invalid username '" username "'") 400)))

    ;; ensure credential exist or create one if user doesn't have one yet
    (if credential-id
      (crud/retrieve-by-id-as-admin credential-id)
      (let [random-pass       (str (u/random-uuid) "?A")
            new-credential-id (user-utils/create-hashed-password user-id random-pass)]
        (user-utils/update-user user-id {:id                  user-id
                                         :credential-password new-credential-id})))

    (let [[cookie-header session] (session-password/create-session-password username user headers href)

          callback-data  {:redirect-url  redirect-url
                          :cookies       (:cookies cookie-header)
                          :hash-password (hashers/derive new-password)}

          callback-reset (create-user-password-reset-callback base-uri user-id callback-data)]


      (if credential-id
        (email-utils/send-password-reset-email callback-reset email-address)
        (email-utils/send-validation-email callback-reset email-address))

      [(dissoc cookie-header :cookies) session])))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
