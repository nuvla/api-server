(ns com.sixsq.nuvla.db.es.script-utils
  (:require [clojure.string :as str]))

(defn- param-key
  [param k]
  (str param "['" k "']"))

(defn- set-field-script [k]
  (str (param-key "ctx._source" k) "=" (param-key "params" k)))

(defn- add-to-array-script [k]
  (str  (param-key "ctx._source" k) ".addAll(" (param-key "params" k) ")"))

(defn- remove-from-array-script [k]
  (str (param-key "ctx._source" k) ".removeAll(" (param-key "params" k) ")"))

(defn- initialize-if-necessary [k]
  (let [field (param-key "ctx._source" k)]
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
