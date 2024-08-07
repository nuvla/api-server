(ns com.sixsq.nuvla.server.resources.hook-reset-password
  "
Reset password hook.
"
  (:require
    [com.sixsq.nuvla.auth.password :as auth-password]
    [com.sixsq.nuvla.server.resources.callback-user-password-set :as callback-pass-set]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.email.utils :as email-utils]
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [com.sixsq.nuvla.server.util.response :as r]))

(def ^:const action "reset-password")

(defn execute
  [{{:keys [username redirect-url]} :body base-uri :base-uri :as _request}]
  (let [{user-id  :id
         email-id :email
         state    :state :as _user} (auth-password/extract-user username)

        {email-address :address :as _email} (when email-id (crud/retrieve-by-id-as-admin email-id))]

    (when-not email-address
      (throw (r/ex-response (str "invalid username '" username "'") 400)))

    (when (= state "SUSPENDED")
      (throw (r/ex-response (format "%s is in 'SUSPENDED' state" user-id) 400)))

    (let [callback-url        (callback-pass-set/create-callback
                                base-uri user-id :expires (u/ttl->timestamp 86400)) ;; 1 day

          ui-set-password-url (str redirect-url "?callback=" (gen-util/encode-uri-component callback-url)
                                   "&type=" (gen-util/encode-uri-component "reset-password")
                                   "&username=" (gen-util/encode-uri-component username))]


      (email-utils/send-password-set-email ui-set-password-url email-address)

      (r/map-response "An email with instructions has been sent to your email address." 200))))



