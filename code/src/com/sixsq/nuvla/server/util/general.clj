(ns com.sixsq.nuvla.server.util.general
  (:require [clojure.string :as str])
  (:import
    (java.net URLDecoder, URLEncoder)
    (java.nio.charset StandardCharsets)))

(defn filter-map-nil-value
  [m]
  (into {} (remove (comp nil? second) m)))

(defn merge-and-ignore-input-immutable-attrs
  [input origin attributes]
  (merge origin (apply dissoc input attributes)))

(defn index-by [coll key-fn]
  (into {} (map (juxt key-fn identity) coll)))

(defn replace-in-str
  [s r-map]
  (reduce-kv (fn [in re v] (str/replace in re v)) s r-map))

(defn encode-uri-component
  [^String s]
  (-> s
      (URLEncoder/encode StandardCharsets/UTF_8)
      (replace-in-str {#"\+"  "%20"
                       #"%21" "!"
                       #"%27" "'"
                       #"%28" "("
                       #"%29" ")"
                       #"%7E" "~"})))

(defn decode-uri-component
  [^String s]
  (-> s
      ^String (replace-in-str {#"%20" "+"
                               #"\!"  "%21"
                               #"\'"  "%27"
                               #"\("  "%28"
                               #"\)"  "%29"
                               #"\~"  "%7E"})
      (URLDecoder/decode StandardCharsets/UTF_8)))
