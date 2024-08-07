(ns com.sixsq.nuvla.server.resources.callback-2fa-activation
  "
Allow a user to activate two factor authentication.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback.utils :as utils]
    [com.sixsq.nuvla.server.resources.two-factor-auth.utils :as auth-2fa]
    [com.sixsq.nuvla.server.resources.user.utils :as user-utils]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "2fa-activation")

(def create-callback (partial callback/create action-name))

(defmethod callback/execute action-name
  [{{user-id :href}         :target-resource
    {:keys [method secret]} :data
    callback-id             :id
    :as                     callback}
   request]
  (try
    (utils/callback-dec-tries callback-id)
    (if (auth-2fa/is-valid-token? method request callback)
      (let [msg     (str "2FA with method '" method "' activated for " user-id
                         ". Callback successfully executed.")
            cred-id (when secret
                      (user-utils/create-totp-credential user-id secret))]
        (user-utils/update-user
          user-id (cond-> {:auth-method-2fa method}
                          cred-id (assoc :credential-totp cred-id)))
        (log/info msg)
        (utils/callback-succeeded! callback-id)
        (r/map-response msg 200 user-id))
      (logu/log-and-throw-400
        (str auth-2fa/msg-wrong-2fa-token " for " user-id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))
