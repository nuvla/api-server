(ns sixsq.nuvla.server.resources.callback.email-utils
  (:require
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]))


(defn validate-email!
  [email-id]
  (try
    (-> (crud/retrieve-by-id-as-admin email-id)
        (u/update-timestamps)
        (assoc :validated true)
        db/edit)
    (catch Exception e
      (or (ex-data e) (throw e)))))


