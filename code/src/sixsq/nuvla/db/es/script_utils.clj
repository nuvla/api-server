(ns sixsq.nuvla.db.es.script-utils
  (:require [clojure.string :as str]))

(defn- set-field-script [k]
  (str "ctx._source." k "=params." k))

(defn- add-to-array-script [k]
  (str "ctx._source." k ".addAll(params." k ")"))

(defn- remove-from-array-script [k]
  (str "ctx._source." k ".removeAll(params." k ")"))

(defn- initialize-if-necessary [k]
  (let [field (str "ctx._source." k)]
    (str "if (" field "== null) " field "= new ArrayList()")))

(def bulk-update-ops->update-script-fn
  {:set (fn [doc]
          (str/join ";" (map (fn [[k _]] (set-field-script (name k))) doc)))
   :add (fn [doc]
          (str/join ";" (mapcat (fn [[k _]] [(initialize-if-necessary (name k))
                                             (remove-from-array-script (name k))
                                             (add-to-array-script (name k))]) doc)))
   :remove (fn [doc]
             (str/join ";" (mapcat (fn [[k _]] [(initialize-if-necessary (name k))
                                                (remove-from-array-script (name k))]) doc)))})

(defn get-update-script [doc op]
  (let [update-script-fn (bulk-update-ops->update-script-fn op)]
    (when update-script-fn
      {:params doc
       :source (update-script-fn doc)})))
