(ns sixsq.nuvla.server.resources.spec.timeseries
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def field-types #{"keyword" "long" "double"})

(s/def ::field-name
  (assoc (st/spec ::core/nonblank-string)
    :json-schema/description "Timeseries field name"))

(s/def ::field-type
  (assoc (st/spec field-types)
    :json-schema/type "string"
    :json-schema/description "Timeseries field name"))

(s/def ::dimension
  (assoc (st/spec (su/only-keys
                    :req-un [::field-name
                             ::field-type]))
    :json-schema/type "map"
    :json-schema/description "Timeseries dimension"))

(s/def ::dimensions
  (-> (st/spec (s/coll-of ::dimension :kind vector? :distinct true :min-count 1))
      (assoc :json-schema/description "Timeseries dimensions")))

(def metric-types #{"gauge" "counter"})

(s/def ::metric-type
  (assoc (st/spec metric-types)
    :json-schema/type "string"
    :json-schema/description "Timeseries metric type"))

(s/def ::optional
  (-> (st/spec boolean?)
      (assoc :json-schema/type "boolean"
             :json-schema/description "optional value ? (default false)")))

(s/def ::metric
  (assoc (st/spec (su/only-keys
                    :req-un [::field-name
                             ::field-type
                             ::metric-type]
                    :opt-un [::optional]))
    :json-schema/type "map"
    :json-schema/description "Timeseries metric"))

(s/def ::metrics
  (-> (st/spec (s/coll-of ::metric :kind vector? :distinct true :min-count 1))
      (assoc :json-schema/description "Timeseries metrics")))

(s/def ::query-name
  (assoc (st/spec ::core/nonblank-string)
    :json-schema/description "Timeseries query name"))

(def query-types #{"standard" "custom-es-query"})

(s/def ::query-type
  (assoc (st/spec query-types)
    :json-schema/type "string"
    :json-schema/description "Timeseries query type"))

(s/def ::aggregation-name
  (assoc (st/spec ::core/nonblank-string)
    :json-schema/description "Timeseries query aggregation name"))

(def aggregation-types #{"avg" "min" "max"})

(s/def ::aggregation-type
  (assoc (st/spec aggregation-types)
    :json-schema/type "string"
    :json-schema/description "Timeseries query aggregation type"))

(s/def ::aggregation
  (assoc (st/spec (su/only-keys
                    :req-un [::aggregation-name
                             ::aggregation-type
                             ::field-name]))
    :json-schema/type "map"
    :json-schema/description "Timeseries query aggregation specification"))

(s/def ::aggregations
  (-> (st/spec (s/coll-of ::aggregation :kind vector? :distinct true))
      (assoc :json-schema/description "Query aggregations")))

(s/def ::query
  (assoc (st/spec (su/only-keys
                    :req-un [::aggregations]))
    :json-schema/type "map"
    :json-schema/description "Timeseries query"))

(s/def ::custom-es-query
  (-> (st/spec (su/constrained-map keyword? any?))
      (assoc :json-schema/type "map"
             :json-schema/description "custom ElasticSearch query")))

(s/def ::query-definition
  (assoc (st/spec (su/only-keys
                    :req-un [::query-name
                             ::query-type]
                    :opt-un [::query
                             ::custom-es-query]))
    :json-schema/type "map"
    :json-schema/description "Timeseries query definition"))

(s/def ::queries
  (-> (st/spec (s/coll-of ::query-definition :kind vector? :distinct true))
      (assoc :json-schema/description "Queries supported by the timeseries")))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::dimensions ::metrics]
                      :opt-un [::queries]}))
