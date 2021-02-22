(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.time :as time]))


(defn get-next-heartbeat
  [nuvlabox-id]
  (try
    (some-> nuvlabox-id
            crud/retrieve-by-id-as-admin
            :refresh-interval
            (* 2)
            (+ 10)
            (time/from-now :seconds)
            time/to-str)
    (catch Exception _
      nil)))


(defn set-online
  [resource request online-prev]
  (let [active-claim (auth/current-active-claim request)
        is-nuvlabox? (str/starts-with? active-claim "nuvlabox/")]
    (cond-> resource
            (some? online-prev) (assoc :online-prev online-prev)
            is-nuvlabox? (assoc :online true))))
