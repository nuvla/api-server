(ns com.sixsq.slipstream.ssclj.resources.callback-email-validation
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.db.impl :as db]
    [com.sixsq.slipstream.ssclj.resources.callback :as callback]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.util.log :as log-util]
    [com.sixsq.slipstream.util.response :as r]))


(def ^:const action-name "email-validation")


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})


(defn validated-email!
  [email-id]
  (try
    (-> (crud/retrieve-by-id-as-admin email-id)
        (u/update-timestamps)
        (assoc :validated true)
        (db/edit admin-opts))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod callback/execute action-name
  [{{:keys [href]} :targetResource :as callback-resource} request]
  (let [{:keys [id validated] :as email} (crud/retrieve-by-id-as-admin href)]
    (if-not validated
      (let [msg (str id " successfully validated")]
        (validated-email! id)
        (log/info msg)
        (r/map-response msg 200 id))
      (log-util/log-and-throw 400 (format "%s already validated" id)))))
