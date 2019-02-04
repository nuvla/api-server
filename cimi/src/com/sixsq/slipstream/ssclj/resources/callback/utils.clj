(ns com.sixsq.slipstream.ssclj.resources.callback.utils
  (:require
    [com.sixsq.slipstream.db.impl :as db]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]))


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})

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
        (db/edit admin-opts))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def callback-succeeded! (partial update-callback-state! "SUCCEEDED"))


(def callback-failed! (partial update-callback-state! "FAILED"))
