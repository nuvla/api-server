(ns sixsq.nuvla.server.resources.callback-user-email-validation
  "Verifies that the email address for a user is valid. On validation, the
   user state is changed from NEW to ACTIVE."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as log-util]
    [sixsq.nuvla.util.response :as r]))

(def ^:const action-name "user-email-validation")


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})


(defn validated-email!
  [user-id]
  (try
    (-> (crud/retrieve-by-id-as-admin user-id)
        (u/update-timestamps)
        (assoc :state "ACTIVE")
        (db/edit admin-opts))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod callback/execute action-name
  [{{:keys [href]} :targetResource :as callback-resource} request]
  (let [{:keys [id state] :as user} (crud/retrieve-by-id-as-admin href)]
    (if (= "NEW" state)
      (let [msg (str "email for " id " successfully validated")]
        (validated-email! id)
        (log/info msg)
        (r/map-response msg 200 id))
      (log-util/log-and-throw 400 (format "%s is not in the 'NEW' state" id)))))
