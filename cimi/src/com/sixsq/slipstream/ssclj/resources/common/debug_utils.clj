(ns com.sixsq.slipstream.ssclj.resources.common.debug-utils
  (:require [clojure.pprint :refer [pp pprint]]))

(defn show
  [x]
  (println "SHOW----")
  (println ":-> " (class x))
  (pprint x)
  (println "--------")
  x)

(defn show-m
  [x m]
  (println "SHOW----" m)
  (println ":-> " (class x))
  (pprint x)
  (println m "--------")
  x)

(defn show-timestamp
  [x]
  (println "\n" (System/currentTimeMillis))
  x)

(def ^:private last-timestamp (ref -1))

(defn start-ts
  [msg x]
  (println msg " START")
  (dosync (ref-set last-timestamp (System/currentTimeMillis)))
  x)

(defn record-ts
  [msg x]
  (let [now (System/currentTimeMillis)
        elapsed (- now @last-timestamp)]
    (if (pos? @last-timestamp)
      (println msg "elapsed :" elapsed "ms")
      (println msg "------------"))
    (dosync (ref-set last-timestamp now))
    x))

(defmacro e->
  [& body]
  `(-> ~@(interleave body (repeat show))))

(defmacro e->>
  [& body]
  `(->> ~@(interleave body (repeat show))))
