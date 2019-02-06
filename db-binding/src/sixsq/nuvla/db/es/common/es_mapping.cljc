(ns sixsq.nuvla.db.es.common.es-mapping
  "Utility for converting clojure.spec definitions to Elasticsearch mappings."
  (:require
    [spec-tools.json-schema :as jsc]
    [clojure.data :refer [diff]]
    [clojure.walk :as w]))


(def dynamic-templates [{:strings {:match              "*"
                                   :match_mapping_type "string"
                                   :mapping            {:type    "keyword"
                                                        :copy_to "fulltext"}}}
                        {:longs {:match              "*"
                                 :match_mapping_type "long"
                                 :mapping            {:type "long"}}}])

(defn keep-key?
  [arg]
  (let [[k v] (seq arg)]                                    ;; seq avoids corner case where we're passed a map
    (or (string? k) (#{:type :enabled :properties :format :copy_to :index} k))))


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
           "ref" (assoc m :type "object")
           "map" (assoc m :type "object")
           "URI" (assoc m :type "keyword")
           "string" (if (= format "date-time")
                      (assoc-date m)
                      (assoc m :type "keyword"))
           "number" (-> m
                        (assoc :type "double")
                        (dissoc :format))
           "integer" (-> m
                         (assoc :type "long")
                         (dissoc :format))
           "long" (-> m
                      (assoc :type "long")
                      (dissoc :format))
           "dateTime" (assoc-date m)
           "array" (:items m)
           "Array" (:items m)
           m)
    enum (let [vs (:enum m)
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
   Elasticsearch mapping."
  [m]
  (if (map? m)
    (let [{:keys [searchable indexed] :or {indexed true, searchable false}} m
          result (cond-> (transform-type->es-type m)
                         searchable (assoc :copy_to "fulltext")
                         (not indexed) (assoc-not-indexed)
                         )]
      (into {} (filter keep-key? result)))
    m))


(defn transform
  ([spec]
   (transform spec nil))
  ([spec options]
   (w/postwalk json-schema->es-mapping (jsc/transform spec options))))


(defn mapping
  [spec]
  (cond-> {:dynamic_templates dynamic-templates}
          spec (merge (-> (transform spec)
                          (dissoc :type)
                          (assoc-in [:properties "fulltext" :type] "text")))))

