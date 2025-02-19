(ns com.sixsq.nuvla.db.es.aggregation
  (:require [clojure.string :as str]
            [jsonista.core :as j]))

(def supported-aggregator {:min           "min"
                           :max           "max"
                           :sum           "sum"
                           :avg           "avg"
                           :stats         "stats"
                           :extendedstats "extended_stats"
                           :value_count   "value_count"
                           :percentiles   "percentiles"
                           :cardinality   "cardinality"
                           :missing       "missing"
                           :terms         "terms"})

(defn agg-entry
  "Give a tuple with the aggregation algo and field-name, adds the aggregation clause to the
   request builder."
  [[algo-kw field]]
  (when-let [algo-name (supported-aggregator algo-kw)]
    (let [tag (str algo-name ":" field)]
      [tag {algo-name {:field (str/replace field #"/" ".")}}])))

(defn aggregators
  "Given the aggregation information in the :cimi-params parameter, add all of the
   aggregation clauses to the aggs map."
  [{:keys [aggregation] :as _cimi-params}]
  (let [entries (mapv agg-entry aggregation)]
    (when (seq entries)
      {:aggs (into {} entries)})))

(defn custom-aggregations
  "Add all of the given custom aggregations clauses to the aggs map."
  [{:keys [custom-aggregations] :as _params}]
  (when (seq custom-aggregations)
    {:aggs custom-aggregations}))

(defn tsds-aggregators
  "Deserialize the tsds aggregation information in the :params parameter."
  [{:keys [tsds-aggregation] :as _params}]
  (some-> tsds-aggregation j/read-value))
