(ns sixsq.nuvla.server.resources.callback-2fa-activation
  "
Allow a user to activate or deactivate two factor authentication.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "2fa-activation")

(def create-callback (partial callback/create action-name))


(defmulti token-is-valid? (fn [_request callback] (-> callback :data :method)))

(defmethod token-is-valid? :default
  [{{user-token :token} :body :as _request}
   {{:keys [token]} :data :as _callback}]
  (and
    (not (str/blank? user-token))
    (= user-token token)))


(defmethod callback/execute action-name
  [{{user-id :href}         :target-resource
    {:keys [method enable]} :data
    callback-id             :id
    :as                     callback}
   request]
  (try
    (utils/callback-dec-tries callback-id)
    (if (token-is-valid? request callback)
      (let [msg (str "2FA with method '" method "' " (if enable "activated" "disabled")
                     " for " user-id ". Callback successfully executed.")]
        (user-utils/update-user user-id {:auth-method-2fa method})
        (log/info msg)
        (utils/callback-succeeded! callback-id)
        (r/map-response msg 200 user-id))
      (logu/log-and-throw-400 (str "Wrong 2FA token for " user-id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))
