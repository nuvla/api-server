(ns sixsq.nuvla.server.resources.callback-user-email-validation
  "
Verifies that the email address for a user is valid. When the execute link is
visited, the user state is changed from NEW to ACTIVE and the email identifier
is marked as validated."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.email-utils :as email-utils]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "user-email-validation")


(defn activate-user!
  [user-id]
  (try
    (-> (crud/retrieve-by-id-as-admin user-id)
        (u/update-timestamps)
        (assoc :state "ACTIVE")
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod callback/execute action-name
  [{callback-id            :id
    {:keys [href]}         :target-resource
    {:keys [redirect-url]} :data :as callback-resource} request]
  (try
    (let [{:keys [id state email] :as user} (crud/retrieve-by-id-as-admin href)]
      (if (= "NEW" state)
        (let [msg (str "email for " id " successfully validated")]
          (email-utils/validate-email! email)
          (activate-user! id)
          (log/info msg)
          (utils/callback-succeeded! callback-id)
          (if redirect-url
            (merge (r/map-response msg 303 id)
                   {:headers {"Location" redirect-url}})
            (r/map-response msg 200 id)))
        (do
          (utils/callback-failed! callback-id)
          (r/map-response (format "%s is not in the 'NEW' state" id) 400))))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))
