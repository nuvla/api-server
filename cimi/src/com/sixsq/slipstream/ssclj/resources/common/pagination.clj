(ns com.sixsq.slipstream.ssclj.resources.common.pagination)

(defn- cut-last
  [coll fst lst]
  (if lst
    (if fst
      (take (inc (- lst fst)) coll)
      (take lst coll))
    coll))

(defn- cut-first
  [coll fst]
  (if fst
    (drop (dec fst) coll)
    coll))

(defn paginate
  [fst lst coll]
  (-> coll
      (cut-first fst)
      (cut-last fst lst)))
