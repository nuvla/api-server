(ns sixsq.nuvla.server.resources.callback-2fa-activation
  "
Allow a user to activate or deactivate two factor authentication.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.callback.utils :as utils]))


(def ^:const action-name "2fa-activation")

(def create-callback (partial callback/create action-name))


(defmulti token-is-valid? :method)

(defmethod token-is-valid? :default
  [{{user-token :token} :body :as _request}
   {{:keys [token]} :data :as _callback}]
  (= user-token token))


(defmethod callback/execute action-name
  [{{user-id :href}  :target-resource
    {:keys [method enable]} :data
    :as              callback}
   request]
  (try
    (if (token-is-valid? request callback)
      (r/map-response "could not validate 2FA token" 400)
      (let [msg (str "Two factor authentication with method '" method "' " (if enable "activated" "disabled")
                     " for " user-id ". Callback successfully executed.")]
        (user-utils/update-user user-id {:auth-method-2fa method})
        (log/info msg)
        (r/map-response msg 200 user-id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))
