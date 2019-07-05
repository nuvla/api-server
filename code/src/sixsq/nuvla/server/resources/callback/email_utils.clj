(ns sixsq.nuvla.server.resources.callback.email-utils
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.auth.utils :as auth]))


(defn validate-email!
  [email-id]
  (try
    (-> (crud/retrieve-by-id-as-admin email-id)
        (u/update-timestamps)
        (assoc :validated true)
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


