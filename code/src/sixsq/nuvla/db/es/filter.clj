(ns sixsq.nuvla.db.es.filter
  (:refer-clojure :exclude [filter])
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [geo.io :as gio]
    [instaparse.transform :as insta-transform]
    [sixsq.nuvla.db.es.query :as query]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.time :as time]))

(defn- strip-quotes
  [s]
  (subs s 1 (dec (count s))))

(defn parse-wkt
  [v]
  (try
    (-> (gio/read-wkt v)
        gio/to-geojson
        (json/read-str :key-fn keyword))
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
  [:Value (time/date-from-str s)])

(defn transform-wkt-value
  [[_ s]]
  [:Value (parse-wkt s)])

(defn transform-attribute
  [& args]
  [:Attribute (str/replace (str/join "" args) #"/" ".")])

(defn transform-comp
  [& args]
  (let [{:keys [Attribute EqOp RelOp GeoOp
                PrefixOp FullTextOp Value] :as m} (into {} args)
        Op (or EqOp RelOp PrefixOp FullTextOp GeoOp)]
    (case Op
      "=" (if (nil? Value) (query/missing Attribute) (query/eq Attribute Value))
      "!=" (if (nil? Value) (query/exists Attribute) (query/ne Attribute Value))
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
    (query/and args)))

(defn transform-or
  [& args]
  (if (= (count args) 1)
    (first args)
    (query/or args)))

(defn transform-filter
  [arg]
  (query/constant-score-query arg))

(def transform-map {:Comp        transform-comp
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
