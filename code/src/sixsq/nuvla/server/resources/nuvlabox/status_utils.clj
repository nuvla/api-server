(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
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


(defn set-nuvlabox-online
  [{:keys [parent online] :as _nuvlabox-status}]
  (try
    (when (some? online)
      (let [{nb-online :online :as nuvlabox} (crud/retrieve-by-id-as-admin parent)]
        (when (not= nb-online online)
          (-> nuvlabox
              (assoc :online online)
              (db/edit {:nuvla/authn auth/internal-identity})))))
    (catch Exception ex
      (log/info parent "update online attribute failed!" ex))))


(defn set-online
  [{:keys [parent] :as resource}]
  (let [next-heartbeat   (get-next-heartbeat parent)
        updated-resource (cond-> (assoc resource :online true)
                                 next-heartbeat (assoc :next-heartbeat next-heartbeat))]
    (set-nuvlabox-online updated-resource)
    updated-resource))


(defn set-inferred-location
  [{:keys [parent inferred-location] :as resource}]
  (try
    (when (some? inferred-location)
      (let [{nb-inferred-location :inferred-location :as nuvlabox} (crud/retrieve-by-id-as-admin parent)]
        (when (not= nb-inferred-location inferred-location)
          (-> nuvlabox
              (assoc :inferred-location inferred-location)
              (db/edit {:nuvla/authn auth/internal-identity})))))
    (catch Exception ex
      (log/info parent "update inferred-location attribute failed!" ex)))
  resource)

