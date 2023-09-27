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


(defn initialize [collection-id options]
  (p/initialize *impl* collection-id options))


(defn add [data]
  (p/add *impl* data))


(defn retrieve [id]
  (p/retrieve *impl* id))


(defn edit [data]
  (p/edit *impl* data))


(defn delete [data]
  (p/delete *impl* data))


(defn query [collection-id options]
  (p/query *impl* collection-id options))


(defn bulk-delete [collection-id options]
  (p/bulk-delete *impl* collection-id options))

(defn bulk-edit [collection-id options]
  (p/bulk-edit *impl* collection-id options))


(defn close []
  (when-let [^Closeable impl *impl*]
    (try
      (unset-impl!)
      (.close impl)
      (catch Exception _))))
