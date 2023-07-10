(ns sixsq.nuvla.server.resources.api-docs.openapi-schema
  "Utility for converting clojure.spec definitions to OpenAPI schemas."
  ;; adapted from sixsq.nuvla.db.es.common.es-mapping
  (:require
    [camel-snake-kebab.core :as csk]
    [clojure.string :as str]
    [clojure.walk :as w]
    [spec-tools.json-schema :as jsc]
    [spec-tools.visitor :as visitor]))


(defn keep-key?
  [arg]
  (let [[k _v] (seq arg)]                                   ;; seq avoids corner case where we're passed a map
    (or (string? k) (#{:type :format :title :description :properties :items} k))))


(defn- set-type-from-first-child
  [[child & _]]
  (cond
    (string? child) {:type "string"}
    (boolean? child) {:type "boolean"}
    (double? child) {:type "number" :format "double"}
    (integer? child) {:type "integer"}
    (int? child) {:type "integer" :format "int64"}
    (seq? child) {:type "array"}
    :else {}))


(defn transform-type->openapi-type
  [{:keys [properties enum type oneOf allOf anyOf openapi-schema format] :as m}]
  (cond
    openapi-schema openapi-schema                           ;; completely replaces the generated schema
    oneOf (first oneOf)
    allOf (first allOf)
    anyOf (first anyOf)
    type (case type
           "map" (assoc m :type "object")
           "resource-id" (assoc m :type "string" :format "uuid")
           "uri" (assoc m :type "string")
           "long" (assoc m :type "number" :format "int64")
           "double" (assoc m :type "number" :format "double")
           "date-time" (-> m (assoc :type "string" :format "date-time"))
           "geo-point" (assoc m :type "array" :items {:type "number" :format "double"})
           "geo-shape" (assoc m :type "object")
           "array" (:items m)
           m)
    enum (let [vs   (:enum m)
               type (set-type-from-first-child vs)]
           (merge (dissoc m :enum) type))
    properties (assoc m :type "object")
    :else m))


(defn json-schema->openapi-schema
  "Function to be used with w/postwalk to transform a JSON schema into an
   OpenAPI schema."
  [m]
  (if (map? m)
    (let [{:keys [title] :as result} (transform-type->openapi-type m)]
      (cond-> (into {} (filter keep-key? result))
              title (assoc :title (-> title keyword name))))
    m))

(defn transform
  ([spec]
   (transform spec nil))
  ([spec options]
   (w/postwalk json-schema->openapi-schema (jsc/transform spec options))
   #_(visitor/visit spec accept-spec options)))


(defn ->openapi-schema
  [spec]
  (transform spec))


(defn schema-name
  [spec-key]
  (-> spec-key str (subs 1) (str/replace "/" ".")))


(defn resource-schema-name
  "Builds the OpenAPI schema name for a resource from the resource type name."
  [{resource-type-name :name :as _resource-metadata}]
  (-> resource-type-name
      ;; remove separators used for suffixes like `— Create`
      (str/replace " \u2014 " "")
      (csk/->PascalCase)))


(defn ->openapi-resource-schema
  "Returns the OpenAPI schema for a resource type."
  [{:keys [spec] :as resource-metadata}]
  (let [schema (->openapi-schema spec)]
    (if (map? schema)
      (let [{:keys [title]} schema]
        (cond-> schema title (assoc :title (resource-schema-name resource-metadata))))
      schema)))


(defn ->openapi-schemas
  "Returns the `schemas` needed to represent operations on the given resource type."
  [{:keys [spec] :as resource-metadata}]
  {(resource-schema-name resource-metadata)
   (->openapi-resource-schema resource-metadata)}
  #_(-> spec
        (visitor/visit (visitor/spec-collector))
        (->> (reduce-kv (fn [m k v]
                          (let [openapi-schema (->openapi-schema v)]
                            (assoc m (schema-name k) #_(csk/->PascalCase (:name v))
                                     openapi-schema)))
                        {}))))
