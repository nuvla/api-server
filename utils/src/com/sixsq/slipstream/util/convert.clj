(ns com.sixsq.slipstream.util.convert
  (:import
    [java.util List Map]))


(defn- clojurify
  [exp]
  (cond
    (instance? Map exp) (into {} (map (fn [[k v]] [(keyword k) v]) exp))
    (instance? List exp) (vec exp)
    :else exp))


(defn walk-clojurify
  [java-map]
  (clojure.walk/prewalk clojurify java-map))
