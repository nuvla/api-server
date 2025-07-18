(ns com.sixsq.nuvla.server.resources.callback-2fa-deactivation
  "
Allow a user to deactivate two-factor authentication.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback.utils :as utils]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.two-factor-auth.utils :as auth-2fa]
    [com.sixsq.nuvla.server.resources.user.utils :as user-utils]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "2fa-deactivation")

(def create-callback (partial callback/create action-name))

(defmethod callback/execute action-name
  [{{user-id :href}  :target-resource
    {:keys [method]} :data
    callback-id      :id
    :as              callback}
   request]
  (try
    (utils/callback-dec-tries callback-id)
    (let [user           (crud/retrieve-by-id-as-admin user-id)
          current-method (:auth-method-2fa user)
          cred-id        (:credential-totp user)
          secret         (when (= current-method auth-2fa/method-totp)
                           (some-> cred-id
                                   crud/retrieve-by-id-as-admin
                                   :secret))
          callback       (cond-> callback
                                 secret (assoc-in [:data :secret] secret))]
      (if (auth-2fa/is-valid-token? current-method request callback)
        (let [msg (str "2FA with method '" current-method "' deactivated for "
                       user-id ". Callback successfully executed.")]
          (user-utils/update-user user-id {:auth-method-2fa method})
          (when cred-id (user-utils/delete-credential cred-id))
          (log/info msg)
          (utils/callback-succeeded! callback-id)
          (r/map-response msg 200 user-id))
        (logu/log-and-throw-400
          (str auth-2fa/msg-wrong-2fa-token " for " user-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))
