(ns sixsq.nuvla.db.impl
  (:require
    [sixsq.nuvla.db.binding :as p])
  (:import
    (java.io Closeable)))

(def ^:dynamic *impl* nil)


(defn set-impl!
  [impl]
  (alter-var-root #'*impl* (constantly impl)))


(defn unset-impl!
  []
  (.unbindRoot #'*impl*))


(defn initialize [collection-id & [options]]
  (p/initialize *impl* collection-id options))


(defn add [data & [options]]
  (p/add *impl* data options))


(defn retrieve [id & [options]]
  (p/retrieve *impl* id options))


(defn edit [data & [options]]
  (p/edit *impl* data options))

(defn scripted-edit [id & [options]]
  (p/scripted-edit *impl* id options))

(defn delete [data & [options]]
  (p/delete *impl* data options))


(defn query [collection-id & [options]]
  (p/query *impl* collection-id options))

(defn query-native [collection-id query]
  (p/query-native *impl* collection-id query))

(defn add-metric [collection-id data & [options]]
  (p/add-metric *impl* collection-id data options))

(defn bulk-insert-metrics [collection-id data & [options]]
  (p/bulk-insert-metrics *impl* collection-id data options))

(defn bulk-delete [collection-id & [options]]
  (p/bulk-delete *impl* collection-id options))

(defn bulk-edit [collection-id & [options]]
  (p/bulk-edit *impl* collection-id options))

(defn create-timeseries [timeseries-id & [options]]
  (p/create-timeseries *impl* timeseries-id options))

(defn retrieve-timeseries [timeseries-id]
  (p/retrieve-timeseries *impl* timeseries-id))

(defn close []
  (when-let [^Closeable impl *impl*]
    (try
      (unset-impl!)
      (.close impl)
      (catch Exception _))))
