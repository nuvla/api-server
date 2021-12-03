(ns sixsq.nuvla.db.es.filter
  (:refer-clojure :exclude [filter])
  (:require
    [clojure.string :as str]
    [clojure.walk :as w]
    [sixsq.nuvla.db.es.query :as query]
    [sixsq.nuvla.server.util.time :as time]))


(defn- strip-quotes
  [s]
  (subs s 1 (dec (count s))))


(defmulti convert
          (fn [v]
            (when (vector? v)
              (first v))))


(defmethod convert :IntValue [[_ ^String s]]
  [:Value (Integer/valueOf s)])


(defmethod convert :DoubleQuoteString [[_ s]]
  [:Value (strip-quotes s)])


(defmethod convert :SingleQuoteString [[_ s]]
  [:Value (strip-quotes s)])


(defmethod convert :BoolValue [[_ ^String s]]
  [:Value (Boolean/valueOf s)])


(defmethod convert :DateValue [[_ ^String s]]
  [:Value (time/date-from-str s)])


(defmethod convert :EnvelopeValue [[_ [long1 lat1] [long2 lat2]]]
  [:Value {:type        "envelope"
           :coordinates [[(min long1 long2) (max lat1 lat2)]
                         [(max long1 long2) (min lat1 lat2)]]}])


(defmethod convert :PolygonValue [[_ & args]]
  [:Value {:type        "polygon"
           :coordinates [(vec args)]}])


(defmethod convert :PointValue [[_ longitude latitude]]
  [longitude latitude])


(defmethod convert :LatitudeValue [[_ ^String latitude]]
  (Double/parseDouble latitude))


(defmethod convert :LongitudeValue [[_ ^String longitude]]
  (Double/parseDouble longitude))

(defmethod convert :PointValue [[_ longitude latitude]]
  [longitude latitude])


(defmethod convert :NullValue [[_ ^String _]]
  [:Value nil])


(defmethod convert :Comp [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)                                          ;; (a=1 and b=2) case
      (let [{:keys [Attribute EqOp RelOp GeoOp PrefixOp FullTextOp Value] :as m} (into {} args)
            Op    (or EqOp RelOp PrefixOp FullTextOp GeoOp)
            order (ffirst args)]
        (case [Op order]
          ["=" :Attribute] (if (nil? Value) (query/missing Attribute) (query/eq Attribute Value))
          ["!=" :Attribute] (if (nil? Value) (query/exists Attribute) (query/ne Attribute Value))

          ["^=" :Attribute] (query/prefix Attribute Value)

          ["==" :Attribute] (query/full-text-search Attribute Value)

          [">=" :Attribute] (query/gte Attribute Value)
          [">" :Attribute] (query/gt Attribute Value)
          ["<=" :Attribute] (query/lte Attribute Value)
          ["<" :Attribute] (query/lt Attribute Value)
          ["in" :Attribute] (query/in Attribute Value)

          ["=" :Value] (if (nil? Value) (query/missing Attribute) (query/eq Attribute Value))
          ["!=" :Value] (if (nil? Value) (query/exists Attribute) (query/ne Attribute Value))

          ["^=" :Value] (query/prefix Attribute Value)

          [">=" :Value] (query/lte Attribute Value)
          [">" :Value] (query/lt Attribute Value)
          ["<=" :Value] (query/gte Attribute Value)
          ["<" :Value] (query/gt Attribute Value)

          m)))))


(defmethod convert :AndExpr
  [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)
      (query/and args))))


(defmethod convert :Filter
  [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)
      (query/or args))))


(defmethod convert :Attribute
  [v]
  [:Attribute (str/replace (str/join "" (rest v)) #"/" ".")])


(defmethod convert :default
  [v]
  v)


(defn filter
  [{:keys [filter] :as _cimi-params}]
  (if filter
    (query/constant-score-query (w/postwalk convert filter))
    (query/match-all-query)))
