(ns sixsq.nuvla.server.resources.timeseries.utils
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.util.response :as r]
            [sixsq.nuvla.server.util.time :as time]))

(def action-insert "insert")
(def action-bulk-insert "bulk-insert")

(defn resource-id->timeseries-index
  [resource-id]
  (str "ts-" (u/id->uuid resource-id)))

(defn dimension->es-property
  [{:keys [field-name field-type]}]
  [field-name {:type                  field-type
               :time_series_dimension true}])

(defn metric->es-property
  [{:keys [field-name field-type metric-type]}]
  [field-name {:type               field-type
               :time_series_metric metric-type}])

(defn ts-resource->mappings
  [{:keys [dimensions metrics]}]
  {:properties
   (into {"@timestamp" {:type   "date"
                        :format "strict_date_optional_time||epoch_millis"}}
         (concat
           (map dimension->es-property dimensions)
           (map metric->es-property metrics)))})

(defn ts-resource->routing-path
  [{:keys [dimensions]}]
  (mapv :field-name dimensions))

(defn create-timeseries
  [resource-id]
  (let [resource     (crud/retrieve-by-id-as-admin resource-id)
        mappings     (ts-resource->mappings resource)
        routing-path (ts-resource->routing-path resource)]
    (db/create-timeseries
      (resource-id->timeseries-index resource-id)
      {:mappings     mappings
       :routing-path routing-path})))

(defn throw-missing-dimensions
  [{:keys [dimensions] :as _timeseries} datapoint]
  (let [missing (->> dimensions
                     (filter #(nil? (get datapoint (keyword (:field-name %))))))]
    (if (empty? missing)
      datapoint
      (throw (r/ex-response
               (str "missing value for dimensions: " (str/join "," (map :field-name missing)))
               400)))))

(defn throw-missing-mandatory-metrics
  [{:keys [metrics] :as _timeseries} datapoint]
  (let [missing (->> metrics
                     (filter #(not (:optional %)))
                     (filter #(nil? (get datapoint (keyword (:field-name %))))))]
    (if (empty? missing)
      datapoint
      (throw (r/ex-response
               (str "missing value for mandatory metrics: " (str/join "," (map :field-name missing)))
               400)))))

(defn throw-wrong-type
  [{:keys [field-name field-type] :as _field} field-value]
  (let [check-type-fn (case field-type
                        "long" int?
                        "double" number?
                        "keyword" string?)]
    (if (check-type-fn field-value)
      field-value
      (throw (r/ex-response
               (str "a value with the wrong type was provided for field " field-name ": " field-value)
               400)))))

(defn throw-wrong-types
  [{:keys [dimensions metrics] :as _timeseries} datapoint]
  (doseq [{:keys [field-name] :as field} (concat dimensions metrics)]
    (throw-wrong-type field (get datapoint (keyword field-name))))
  datapoint)

(defn throw-extra-keys
  [{:keys [dimensions metrics] :as _timeseries} datapoint]
  (let [extra-keys (set/difference (set (keys (dissoc datapoint :timestamp)))
                                   (->> (concat dimensions metrics)
                                        (map (comp keyword :field-name))
                                        set))]
    (if (empty? extra-keys)
      datapoint
      (throw (r/ex-response
               (str "unexpected keys: " (str/join "," extra-keys))
               400)))))

(defn validate-datapoint
  [timeseries datapoint]
  (->> datapoint
       (throw-missing-dimensions timeseries)
       (throw-missing-mandatory-metrics timeseries)
       (throw-wrong-types timeseries)
       (throw-extra-keys timeseries)))

(defn validate-datapoints
  [timeseries datapoints]
  (doseq [datapoint datapoints]
    (validate-datapoint timeseries datapoint))
  datapoints)

(defn add-timestamp
  [{:keys [timestamp] :as datapoint}]
  (cond-> datapoint
          (not timestamp) (assoc :timestamp (time/now-str))))
