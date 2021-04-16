(ns sixsq.nuvla.server.resources.hook-reset-password
  "
Reset password hook.
"
  (:require
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.server.resources.callback-user-password-set :as callback-pass-set]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.util.response :as r]))

(def ^:const action "reset-password")

(defn execute
  [{{:keys [username redirect-url]} :body base-uri :base-uri :as request}]
  (let [{user-id  :id
         email-id :email
         state    :state :as user} (auth-password/extract-user username)

        {email-address :address :as email} (when email-id (crud/retrieve-by-id-as-admin email-id))]

    (when-not email-address
      (throw (r/ex-response (str "invalid username '" username "'") 400)))

    (when (= state "SUSPENDED")
      (throw (r/ex-response (format "%s is in 'SUSPENDED' state" user-id) 400)))

    (let [callback-url        (callback-pass-set/create-callback
                                base-uri user-id :expires (u/ttl->timestamp 86400)) ;; 1 day

          ui-set-password-url (str redirect-url "?callback=" (codec/url-encode callback-url)
                                   "&type=" (codec/url-encode "reset-password")
                                   "&username=" (codec/url-encode username))]


      (email-utils/send-password-set-email ui-set-password-url email-address)

      (r/map-response "An email with instructions has been sent to your email address." 200))))



