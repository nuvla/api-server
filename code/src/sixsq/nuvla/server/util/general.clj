(ns sixsq.nuvla.server.util.general)

(defn filter-map-nil-value
  [m]
  (into {} (remove (comp nil? second) m)))
