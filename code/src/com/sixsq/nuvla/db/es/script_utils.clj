(ns com.sixsq.nuvla.db.es.script-utils
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

(defmulti bulk-update-script (fn [_ operation] operation))

(defmethod bulk-update-script :set
  [doc _]
  (str/join ";" (map (fn [[k _]] (set-field-script (name k))) doc)))

(defmethod bulk-update-script :add
  [doc _]
  (str/join ";" (mapcat (fn [[k _]] [(initialize-if-necessary (name k))
                                     (remove-from-array-script (name k))
                                     (add-to-array-script (name k))]) doc)))

(defmethod bulk-update-script :remove
  [doc _]
  (str/join ";" (mapcat (fn [[k _]] [(initialize-if-necessary (name k))
                                     (remove-from-array-script (name k))]) doc)))

(defn get-update-script
  [doc operation]
  {:params doc
   :source (bulk-update-script doc operation)})
