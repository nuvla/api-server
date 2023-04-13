(ns sixsq.nuvla.db.es.script-utils
  (:require [clojure.string :as str]))

(defn- set-field-script [k]
  (str "ctx._source." k "=params." k))

(defn- add-to-array-script [k]
  (str "ctx._source." k ".addAll(params." k ")"))

(defn- remove-from-array-script [k]
  (str "ctx._source." k ".removeAll(params." k ")"))

(def bulk-update-ops->update-script-fn
  {:set (fn [doc]
          (str/join ";" (map (fn [[k _]] (set-field-script (name k))) doc)))
   :add (fn [doc]
          (str/join ";" (map (fn [[k _]] (add-to-array-script (name k))) doc)))
   :remove (fn [doc]
             (str/join ";" (map (fn [[k _]] (remove-from-array-script (name k))) doc)))})

(defn get-update-script [doc op]
  (let [update-script-fn (bulk-update-ops->update-script-fn op)]
    (when update-script-fn
      {:params doc
       :source (update-script-fn doc)})))