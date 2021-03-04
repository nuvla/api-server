(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
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


(defn set-nuvlabox-online
  [{:keys [parent online online-prev] :as nuvlabox-status}]
  ;;TODO retrieve of nuvlabox can be deleted in the futur after a release
  (let [nuvlabox (crud/retrieve-by-id-as-admin parent)]
    (when (and
           (some? online)
           (or (not= online-prev
                     online)
               (nil? (:online nuvlabox))))
     (try
       (-> (crud/retrieve-by-id-as-admin parent)
           (u/update-timestamps)
           (assoc :online online)
           (db/edit {:nuvla/authn auth/internal-identity}))
       (catch Exception ex
         (log/info parent "update online attribute failed!" ex))))))


(defn set-online
  [resource request online-prev]
  (let [active-claim     (auth/current-active-claim request)
        is-nuvlabox?     (str/starts-with? active-claim "nuvlabox/")
        updated-resource (cond-> resource
                                 (some? online-prev) (assoc :online-prev online-prev)
                                 is-nuvlabox? (assoc :online true))]
    (set-nuvlabox-online updated-resource)
    updated-resource))
