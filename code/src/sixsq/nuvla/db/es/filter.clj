(ns sixsq.nuvla.db.es.filter
  (:refer-clojure :exclude [filter])
  (:require
    [clojure.string :as str]
    [clojure.walk :as w]
    [sixsq.nuvla.db.es.query :as query]
    [sixsq.nuvla.db.utils.time-utils :as time]))


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
  [:Value (time/to-time-or-date s)])


(defmethod convert :NullValue [[_ ^String s]]
  [:Value nil])


(defmethod convert :Comp [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)                                          ;; (a=1 and b=2) case
      (let [{:keys [Attribute EqOp RelOp PrefixOp FullTextOp  Value] :as m} (into {} args)
            Op (or EqOp RelOp PrefixOp FullTextOp)
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
  [{:keys [filter] :as cimi-params}]
  (if filter
    (query/constant-score-query (w/postwalk convert filter))
    (query/match-all-query)))
