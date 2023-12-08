(ns sixsq.nuvla.server.util.general)

(defn filter-map-nil-value
  [m]
  (into {} (remove (comp nil? second) m)))

(defn merge-and-ignore-input-immutable-attrs
  [input origin attributes]
  (merge origin (apply dissoc input attributes)))

(defn index-by [coll key-fn]
  (into {} (map (juxt key-fn identity) coll)))
