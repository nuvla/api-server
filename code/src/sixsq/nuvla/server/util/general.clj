(ns sixsq.nuvla.server.util.general)

(defn filter-map-nil-value
  [m]
  (into {} (remove (comp nil? second) m)))

(defn merge-and-ignore-input-immutable-attrs
  [input origin attributes]
  (merge origin (apply dissoc input attributes)))
