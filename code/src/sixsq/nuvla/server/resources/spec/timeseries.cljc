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
  (-> (st/spec (s/coll-of ::dimension :kind vector? :distinct true))
      (assoc :json-schema/description "Timeseries dimensions")))

(def metric-types #{"gauge" "counter"})

(s/def ::metric-type
  (assoc (st/spec metric-types)
    :json-schema/type "string"
    :json-schema/description "Timeseries metric type"))

(s/def ::optional
  (-> (st/spec boolean?)
      (assoc :name "optional"
             :json-schema/type "boolean"
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
  (-> (st/spec (s/coll-of ::metric :kind vector? :distinct true))
      (assoc :json-schema/description "Timeseries metrics")))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::dimensions ::metrics]}))

