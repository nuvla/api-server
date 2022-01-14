(ns sixsq.nuvla.server.resources.callback-create-session-2fa
  "
Allow a user to validate session with two factor authentication.
"
  (:require
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-2fa-activation :as callback-2fa-activation]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "session-2fa-creation")

(def create-callback (partial callback/create action-name))

(defmethod callback/execute action-name
  [{{session-id :href} :target-resource
    {:keys [headers]}  :data
    callback-id        :id
    :as                callback}
   request]
  (try
    (utils/callback-dec-tries callback-id)
    (let [{user-id :user :as current-session} (crud/retrieve-by-id-as-admin session-id)]
      (if (callback-2fa-activation/token-is-valid? request callback)
        (let [cookie-info (cookies/create-cookie-info user-id
                                                      :session-id session-id
                                                      :headers headers
                                                      :client-ip (:client-ip current-session))
              cookie (cookies/create-cookie cookie-info)
              expires (ts/rfc822->iso8601 (:expires cookie))
              claims (:claims cookie-info)
              groups (:groups cookie-info)
              updated-session (cond-> (assoc current-session
                                        :expiry expires)
                                      claims (assoc :roles claims)
                                      groups (assoc :groups groups))
              {:keys [status] :as resp} (sutils/update-session session-id updated-session)]
          (if (not= status 200)
            resp
            (let [cookie-tuple [authn-info/authn-cookie cookie]]
              (utils/callback-succeeded! callback-id)
              (r/response-created session-id cookie-tuple))))
        (logu/log-and-throw-400 (str callback-2fa-activation/msg-wrong-2fa-token " for " user-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))
