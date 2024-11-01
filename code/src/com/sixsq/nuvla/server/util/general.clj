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

(defn truncate
  [s n]
  (when-let [s-size (some-> s count)]
    (if (> s-size n)
      (let [half-size (int (/ n 2))]
        (str (subs s 0 half-size) "\n...\n" (subs s (- s-size half-size) s-size)))
      s)))

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


(defn safe-subs
  "Returns the substring of s beginning at start inclusive, and ending
  at end (defaults to length of string), exclusive."
  (^String [^String s start]
   (safe-subs s start (.length s)))
  (^String [^String s start end]
   (try
     (subs s start end)
     (catch StringIndexOutOfBoundsException _))))
