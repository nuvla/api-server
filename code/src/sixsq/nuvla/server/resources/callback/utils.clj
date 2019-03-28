(ns sixsq.nuvla.server.resources.callback.utils
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]))

(defn executable?
  [{:keys [state expires]}]
  (and (= state "WAITING")
       (u/not-expired? expires)))


(defn update-callback-state!
  [state callback-id]
  (try
    (-> (crud/retrieve-by-id-as-admin callback-id)
        (u/update-timestamps)
        (assoc :state state)
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def callback-succeeded! (partial update-callback-state! "SUCCEEDED"))


(def callback-failed! (partial update-callback-state! "FAILED"))
