(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.time :as time]
    [sixsq.nuvla.auth.utils :as auth]
    [clojure.string :as str]))


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


(defn set-online
  [resource request]
  (let [active-claim (auth/current-active-claim request)
        is-nuvlabox? (str/starts-with? active-claim "nuvlabox/")]
    (cond-> resource
            is-nuvlabox? (assoc :online true))))
