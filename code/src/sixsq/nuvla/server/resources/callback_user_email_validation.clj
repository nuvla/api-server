(ns sixsq.nuvla.server.resources.callback-user-email-validation
  "Verifies that the email address for a user is valid. On validation, the
   user state is changed from NEW to ACTIVE."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as log-util]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "user-email-validation")


(defn validate_email!
  [email-id]
  (try
    (-> (crud/retrieve-by-id-as-admin email-id)
        (u/update-timestamps)
        (assoc :validated true)
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn activate-user!
  [user-id]
  (try
    (-> (crud/retrieve-by-id-as-admin user-id)
        (u/update-timestamps)
        (assoc :state "ACTIVE")
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn validated-email!
  [user-id email-id]
  (validate_email! email-id)
  (activate-user! user-id))


(defmethod callback/execute action-name
  [{{:keys [href]} :target-resource
    {:keys [redirect-url]} :data :as callback-resource} request]
  (let [{:keys [id state email] :as user} (crud/retrieve-by-id-as-admin href)]
    (if (= "NEW" state)
      (let [msg (str "email for " id " successfully validated")]
        (validated-email! id email)
        (log/info msg)
        (if redirect-url
          (merge (r/map-response msg 303 id)
                 {:headers {"Location" redirect-url}})
          (r/map-response msg 200 id)))
      (log-util/log-and-throw 400 (format "%s is not in the 'NEW' state" id)))))
