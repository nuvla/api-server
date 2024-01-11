(ns sixsq.nuvla.db.es.common.es-mapping
  "Utility for converting clojure.spec definitions to Elasticsearch mappings."
  (:require
    [clojure.walk :as w]
    [spec-tools.json-schema :as jsc]))


(def dynamic-templates-mapping
  [{:strings {:match              "*"
              :match_mapping_type "string"
              :mapping            {:type    "keyword"
                                   :copy_to "fulltext"}}}
   {:longs {:match              "*"
            :match_mapping_type "long"
            :mapping            {:type "long"}}}])

(defn keep-key?
  [arg]
  (let [[k _v] (seq arg)]                                   ;; seq avoids corner case where we're passed a map
    (or (string? k) (#{:type :enabled :properties :format :copy_to :index
                       :time_series_metric :time_series_dimension :field-type} k))))


(defn assoc-date
  [m]
  (assoc m :type "date" :format "strict_date_optional_time||epoch_millis"))


(defn- set-type-from-first-child
  [[child & _]]
  (cond
    (string? child) {:type "keyword"}
    (boolean? child) {:type "boolean"}
    (double? child) {:type "double"}
    (integer? child) {:type "long"}
    (int? child) {:type "integer"}
    (seq? child) {:type "nested"}
    :else {}))


(defn transform-type->es-type
  [{:keys [properties enum type oneOf allOf anyOf es-mapping format] :as m}]
  (cond
    es-mapping es-mapping                                   ;; completely replaces the generated mapping
    oneOf (first oneOf)
    allOf (first allOf)
    anyOf (first anyOf)
    type (case type
           "map" (assoc m :type "object")
           "geo-point" (assoc m :type "geo_point"
                                :ignore_z_value true)
           "geo-shape" (-> m
                           (assoc :type "geo_shape"
                                  :ignore_z_value true)
                           (dissoc :properties :required))
           "string" (if (= format "date-time")
                      (assoc-date m)
                      (assoc m :type "keyword"))
           "resource-id" (assoc m :type "keyword")
           "uri" (assoc m :type "keyword")
           "number" (-> m
                        (assoc :type "double")
                        (dissoc :format))
           "double" (-> m
                        (assoc :type "double")
                        (dissoc :format))
           "integer" (-> m
                         (assoc :type "long")
                         (dissoc :format))
           "long" (-> m
                      (assoc :type "long")
                      (dissoc :format))
           "date-time" (assoc-date m)
           "array" (:items m)
           m)
    enum (let [vs   (:enum m)
               type (set-type-from-first-child vs)]
           (merge (dissoc m :enum) type))
    properties (assoc m :type "object")
    :else m)
  )


(defn assoc-not-indexed
  [{:keys [type] :as m}]
  (if (= type "object")
    (-> m
        (assoc :enabled false)
        (dissoc :properties))
    (assoc m :index false)))


(defn json-schema->es-mapping
  "Function to be used with w/postwalk to transform a JSON schema into an
   Elasticsearch mapping. The default is that the field will be indexed and be
   included in the 'fulltext' pseudo-field when marked as an indexed keyword."
  [m]
  (if (map? m)
    (let [{:keys [fulltext indexed] :or {indexed true, fulltext false}} m
          {:keys [type field-type] :as updated-m} (transform-type->es-type m)
          result (cond-> updated-m
                         (and (= type "keyword") fulltext) (assoc :copy_to "fulltext")
                         (not indexed) (assoc-not-indexed)
                         (= field-type :dimension) (-> (dissoc :field-type)
                                                       (assoc :time_series_dimension true))
                         (= field-type :metric-gauge) (-> (dissoc :field-type)
                                                          (assoc :time_series_metric "gauge"))
                         (= field-type :metric-counter) (-> (dissoc :field-type)
                                                            (assoc :time_series_metric "counter")))]
      (if (map? result)
        (->> result
             (reduce (fn [m [k {:keys [field-type] :as v}]]
                       (case field-type
                         :timestamp (assoc m "@timestamp" (dissoc v :field-type))
                         (assoc m k v))) {})
             (filter keep-key?)
             (into {}))
        (->> result
             (filter keep-key?)
             (into {}))))
    m))


(defn transform
  ([spec]
   (transform spec nil))
  ([spec options]
   (w/postwalk json-schema->es-mapping (jsc/transform spec options))))


(defn mapping
  ([spec]
   (mapping spec nil))
  ([spec {:keys [dynamic-templates fulltext] :or {dynamic-templates true fulltext true}}]
   (cond-> {}
           dynamic-templates (assoc :dynamic_templates dynamic-templates-mapping)
           spec (merge (cond-> (dissoc (transform spec) :type)
                               fulltext (assoc-in [:properties "fulltext" :type] "text"))))))

