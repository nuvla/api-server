(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]))

(def DENORMALIZED_FIELD [:inferred-location :nuvlabox-engine-version])

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
          (db/edit new-nuvlabox nil))))))

(defn nuvlabox-request?
  [request]
  (str/starts-with? (auth/current-active-claim request) "nuvlabox/"))

(defn nuvlabox-does-not-support-heartbeat
  [nuvlabox-id]
  (-> nuvlabox-id
      crud/retrieve-by-id-as-admin
      utils/has-heartbeat-support?
      not))

(defn heartbeat
  [request {:keys [parent] :as _nuvlabox-status}]
  (try
    (if (and
            (nuvlabox-request? request)
            (nuvlabox-does-not-support-heartbeat parent))
      (-> parent
          (crud/do-action-as-user
            utils/action-heartbeat (auth/current-authentication request))
          (get-in [:body :jobs] []))
      [])
    (catch Exception e
      (log/errorf "Nuvlabox status heartbeat failed: %s\n%s" parent (str e)))))
