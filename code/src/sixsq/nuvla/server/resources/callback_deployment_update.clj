(ns sixsq.nuvla.server.resources.callback-deployment-update
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.notification :refer [resource-type]]
    [sixsq.nuvla.server.resources.deployment :as depl]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "deployment-update")


(defn update-deployment!
  [deployment-id data request]
  (try
    (-> (crud/retrieve-by-id-as-admin deployment-id)
        (u/update-timestamps)
        (merge data)
        (db/edit nil))
    (catch Exception e
      (or (ex-data e) (throw e))))
  (depl/update-action-mpl request))


(defmethod callback/execute action-name
  [{{id :href} :target-resource :as callback-resource} request]
  (let [msg (str id " update requested")]
    (update-deployment! id (:data callback-resource) request)
    (log/info msg)
    (r/map-response msg 200 id)))
