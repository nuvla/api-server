(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.impl :as db]
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
    (catch Exception ex
      (log/errorf "Unable to get next heartbeat for %1: %2" nuvlabox-id ex))))

(def DENORMALIZED_FIELD [:online :inferred-location :nuvlabox-engine-version])

(defn status-fields-to-denormalize
  [nuvlabox-status]
  (->> DENORMALIZED_FIELD
       (select-keys nuvlabox-status)
       (filter (comp some? second))
       (into {})))

(defn denormalize-changes-nuvlabox
  [{:keys [parent] :as nuvlabox-status}]
  (let [propagate-status (status-fields-to-denormalize nuvlabox-status)]
    (when (seq propagate-status)
      (let [nuvlabox     (crud/retrieve-by-id-as-admin parent)
            new-nuvlabox (merge nuvlabox propagate-status)]
        (when (not= nuvlabox new-nuvlabox)
          (db/edit new-nuvlabox))))))
