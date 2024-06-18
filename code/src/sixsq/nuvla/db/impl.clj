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

(defn query-native [index query]
  (p/query-native *impl* index query))

(defn bulk-delete [collection-id & [options]]
  (p/bulk-delete *impl* collection-id options))

(defn bulk-edit [collection-id & [options]]
  (p/bulk-edit *impl* collection-id options))

(defn create-timeseries [index & [options]]
  (p/create-timeseries *impl* index options))

(defn edit-timeseries [index & [options]]
  (p/edit-timeseries *impl* index options))

(defn retrieve-timeseries [timeseries-id]
  (p/retrieve-timeseries *impl* timeseries-id))

(defn add-timeseries-datapoint [index data & [options]]
  (p/add-timeseries-datapoint *impl* index data options))

(defn bulk-insert-timeseries-datapoints [index data & [options]]
  (p/bulk-insert-timeseries-datapoints *impl* index data options))

(defn delete-timeseries [index & [options]]
  (p/delete-timeseries *impl* index options))

(defn close []
  (when-let [^Closeable impl *impl*]
    (try
      (unset-impl!)
      (.close impl)
      (catch Exception _))))
