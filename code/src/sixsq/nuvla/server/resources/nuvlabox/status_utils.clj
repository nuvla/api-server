(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.time :as time]))


(defn get-next-heartbeat
  [nuvlabox-id]
  (try
    (some-> nuvlabox-id
            crud/retrieve-by-id-as-admin
            :refresh-interval
            (time/from-now :seconds)
            time/to-str)
    (catch Exception _
      nil)))
