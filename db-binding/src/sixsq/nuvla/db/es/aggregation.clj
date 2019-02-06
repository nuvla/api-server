(ns sixsq.nuvla.db.es.aggregation
  (:require [clojure.string :as str])
  (:import
    (org.elasticsearch.action.search SearchRequestBuilder)
    (org.elasticsearch.search.aggregations AggregationBuilder)))

(defmacro aggregator-constructor-fn
  "This takes a form that must evaluate to a string at compile time. The
   string is used to create an aggregator constructor function that will take a
   field name as the argument."
  [algo-form]
  (let [class (symbol "org.elasticsearch.search.aggregations.AggregationBuilders")
        algo-name (eval algo-form)
        algo-sym (symbol algo-name)]
    `(fn [field#]
       (let [tag# (str (str/lower-case ~algo-name) ":" field#)]
         (doto (. ~class ~algo-sym tag#)
           (.field (str/replace field# #"/" ".")))))))

(def aggregator-constructors {:min           (aggregator-constructor-fn "min")
                              :max           (aggregator-constructor-fn "max")
                              :sum           (aggregator-constructor-fn "sum")
                              :avg           (aggregator-constructor-fn "avg")
                              :stats         (aggregator-constructor-fn "stats")
                              :extendedstats (aggregator-constructor-fn "extendedStats")
                              :count         (aggregator-constructor-fn "count")
                              :percentiles   (aggregator-constructor-fn "percentiles")
                              :cardinality   (aggregator-constructor-fn "cardinality")
                              :missing       (aggregator-constructor-fn "missing")
                              :terms         (aggregator-constructor-fn "terms")})

(defn add-aggregator
  "Add aggregators for all of the fields associated with a single aggregation
   function. Intended to be used from within a reduction."
  [^SearchRequestBuilder request-builder [algo-kw fields]]
  (when-let [f (aggregator-constructors algo-kw)]
    (doseq [field fields]
      (let [^AggregationBuilder aggregator (f field)]
        (.addAggregation request-builder aggregator))))
  request-builder)

(defn add-aggregators
  "Adds aggregators to the search request builder from the value of the
   metric parameter. This parameter has keys (as keywords) that identifies
   the algorithm to use and values that are vectors of field names."
  [^SearchRequestBuilder request-builder {:keys [aggregation] :as cimi-params}]
  (reduce add-aggregator request-builder aggregation))
