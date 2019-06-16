(ns sixsq.nuvla.server.resources.callback-module-update
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.notification :refer [resource-type]]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "module-update")


(defn update-component!
  [module-id data]
  (try
    (-> (crud/retrieve-by-id-as-admin module-id)
        (merge data)
        (db/edit nil))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod callback/execute action-name
  [{{id :href} :target-resource :as callback-resource} request]
  (let [msg (str id " successfully updated")]
    (update-component! id (:data callback-resource))
    (log/info msg)
    (r/map-response msg 200 id)))
