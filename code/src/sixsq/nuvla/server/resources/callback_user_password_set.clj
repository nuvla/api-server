(ns sixsq.nuvla.server.resources.callback-user-password-set
  "
Allow a user to set a new password when the execute URL is visited.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-validation]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "user-password-set")

(def create-callback (partial callback/create action-name))


(defmethod callback/execute action-name
  [{callback-id     :id
    {user-id :href} :target-resource :as _callback-resource}
   {{:keys [new-password]} :body :as _request}]
  (when-not (hashed-password/acceptable-password? new-password)
    (throw (r/ex-response hashed-password/acceptable-password-msg 400)))
  (try
    (let [{:keys [id state] :as user} (crud/retrieve-by-id-as-admin user-id)]
      (when (= state "SUSPENDED")
        (utils/callback-failed! callback-id)
        (throw (r/ex-response (format "%s is not in the 'NEW' or 'ACTIVE' state" id) 400)))
      (when (= state "NEW") (user-email-validation/activate-user-new-active user))
      (let [new-credential-id (user-utils/create-hashed-password id new-password)
            msg               (str "set password for " id " successfully executed")]
        (user-utils/update-user id {:id                  id
                                    :credential-password new-credential-id})
        (log/info msg)
        (r/map-response msg 200 id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))
