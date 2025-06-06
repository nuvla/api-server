(ns com.sixsq.nuvla.db.es.filter
  (:refer-clojure :exclude [filter])
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.db.es.query :as query]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.util.time :as time]
    [geo.io :as gio]
    [instaparse.transform :as insta-transform]
    [jsonista.core :as j]))

(defn- strip-quotes
  [s]
  (subs s 1 (dec (count s))))

(defn parse-wkt
  [v]
  (try
    (-> (gio/read-wkt v)
        gio/to-geojson
        (j/read-value j/keyword-keys-object-mapper))
    (catch Exception e
      (logu/log-and-throw-400
        (str "invalid WKT format '" v "'. " (ex-message e) ".")))))

(defn transform-string-value
  [s]
  [:Value (strip-quotes s)])

(defn transform-null-value
  [_]
  [:Value nil])

(defn transform-int-value
  [^String s]
  [:Value (Integer/valueOf s)])

(defn transform-bool-value
  [^String s]
  [:Value (Boolean/valueOf s)])

(defn transform-date-value
  [^String s]
  [:Value (time/parse-date s)])

(defn transform-wkt-value
  [[_ s]]
  [:Value (parse-wkt s)])

(defn transform-values
  [& args]
  [:Value (mapv second args)])

(defn transform-attribute
  [& args]
  [:Attribute (str/replace (str/join "" args) #"/" ".")])

(defn eq
  [term value]
  (if (coll? value)
    (query/terms-query term value)
    (query/term-query term value)))

(defn transform-comp
  [& args]
  (let [{:keys [Attribute EqOp RelOp GeoOp PrefixOp FullTextOp
                Value] :as m} (into {} args)
        Op (or EqOp RelOp PrefixOp FullTextOp GeoOp)]
    (case Op
      "=" (if (nil? Value)
            (query/not-clause (query/exists Attribute))
            (eq Attribute Value))
      "!=" (if (nil? Value)
             (query/exists Attribute)
             (query/not-clause (eq Attribute Value)))
      "^=" (query/prefix Attribute Value)
      "==" (query/full-text-search Attribute Value)
      ">=" (query/gte Attribute Value)
      ">" (query/gt Attribute Value)
      "<=" (query/lte Attribute Value)
      "<" (query/lt Attribute Value)
      "intersects" (query/geo-shape Attribute Op Value)
      "disjoint" (query/geo-shape Attribute Op Value)
      "within" (query/geo-shape Attribute Op Value)
      "contains" (query/geo-shape Attribute Op Value)
      m)))

(defn transform-and
  [& args]
  (if (= (count args) 1)
    (first args)
    (query/and-clauses args)))

(defn transform-or
  [& args]
  (if (= (count args) 1)
    (first args)
    (query/or-clauses args)))

(defn transform-filter
  [arg]
  (query/constant-score-query arg))

(def transform-map {:Comp        transform-comp
                    :Values      transform-values
                    :StringValue transform-string-value
                    :NullValue   transform-null-value
                    :IntValue    transform-int-value
                    :BoolValue   transform-bool-value
                    :DateValue   transform-date-value
                    :WktValue    transform-wkt-value
                    :Attribute   transform-attribute
                    :And         transform-and
                    :Or          transform-or
                    :Filter      transform-filter})

(defn transform
  [hiccup-tree]
  (insta-transform/transform transform-map hiccup-tree))

(defn filter
  [{:keys [filter] :as _cimi-params}]
  (if filter
    (transform filter)
    (query/match-all-query)))
