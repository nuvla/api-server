(ns sixsq.nuvla.server.resources.callback-user-password-reset
  "Verifies that the user is able to validate the reset password from his email address.
On validation, the password is changed and the user is logged in."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "user-reset-password")


(defn update-password!
  [credential-id hash-password]
  (try
    (-> (crud/retrieve-by-id-as-admin credential-id)
        (u/update-timestamps)
        (assoc :hash hash-password)
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod callback/execute action-name
  [{{:keys [href]} :targetResource data :data :as callback-resource} request]
  (let [{:keys [credential-password id] :as user} (crud/retrieve-by-id-as-admin href)
        {:keys [redirectURI cookies hash-password]} data
        msg (str "reset password for " id " successfully executed")]
    (update-password! credential-password hash-password)
    (log/info msg)
    (if redirectURI
      (merge (r/map-response msg 303 id)
             {:headers {"Location" redirectURI}, :cookies cookies})
      (merge (r/map-response msg 200 id)
             {:cookies cookies}))))
