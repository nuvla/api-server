(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.util.time :as time]))

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
      (db/scripted-edit parent {:doc propagate-status}))))

(defn status-telemetry-attributes
  [nuvlabox-status
   {:keys [refresh-interval]
    :or   {refresh-interval nb-utils/default-refresh-interval}
    :as   _nuvlabox}]
  (assoc nuvlabox-status
    :last-telemetry (time/now-str)
    :next-telemetry (nb-utils/compute-next-report refresh-interval #(+ % 10))))
