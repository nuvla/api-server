(ns sixsq.nuvla.server.resources.timeseries.utils
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.util.response :as r]
            [sixsq.nuvla.server.util.time :as time]))

(def action-insert "insert")
(def action-bulk-insert "bulk-insert")
(def action-data "data")

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
  [{:keys [id] :as resource}]
  (let [mappings     (ts-resource->mappings resource)
        routing-path (ts-resource->routing-path resource)]
    (db/create-timeseries
      (resource-id->timeseries-index id)
      {:mappings     mappings
       :routing-path routing-path})))

(defn throw-dimensions-can-only-be-appended
  [{{new-dimensions :dimensions} :body :as request}
   {current-dimensions :dimensions :as _current}]
  (when current-dimensions
    (when-not (and (>= (count new-dimensions) (count current-dimensions))
                   (= current-dimensions
                      (subvec new-dimensions 0 (count current-dimensions))))
      (throw (r/ex-response "dimensions can only be appended" 400))))
  request)

(defn throw-metrics-can-only-be-added
  [{{new-metrics :metrics} :body :as request}
   {current-metrics :metrics :as _current}]
  (when-not (every? (fn [{:keys [field-name] :as current-metric}]
                      (= current-metric
                         (->> new-metrics
                              (filter #(= field-name (:field-name %)))
                              first)))
                    current-metrics)
    (throw (r/ex-response "metrics can only be added" 400)))
  request)

(defn edit-timeseries
  [{:keys [id] :as resource}]
  (let [mappings     (ts-resource->mappings resource)
        routing-path (ts-resource->routing-path resource)]
    (db/edit-timeseries
      (resource-id->timeseries-index id)
      {:mappings     mappings
       :routing-path routing-path})))

(defn delete-timeseries
  [resource-id]
  (db/delete-timeseries
    (resource-id->timeseries-index resource-id)))

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
    (some->> (get datapoint (keyword field-name))
             (throw-wrong-type field)))
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

(defn throw-outside-acceptable-time-range
  [_timeseries {:keys [timestamp] :as datapoint}]
  (let [ts              (time/parse-date timestamp)
        now             (time/now)
        look-ahead-time (time/duration-unit 2 :hours)
        look-back-time  (time/duration-unit 7 :days)
        start-time      (time/minus now look-back-time)
        end-time        (time/plus now look-ahead-time)]
    (if (and (time/before? start-time ts) (time/before? ts end-time))
      datapoint
      (throw (r/ex-response
               (str "timestamp is outside acceptable range: " ts " not in [" start-time " - " end-time "]")
               400)))))

(defn validate-datapoint
  [timeseries datapoint]
  (->> datapoint
       (throw-missing-dimensions timeseries)
       (throw-missing-mandatory-metrics timeseries)
       (throw-wrong-types timeseries)
       (throw-extra-keys timeseries)
       (throw-outside-acceptable-time-range timeseries)))

(defn validate-datapoints
  [timeseries datapoints]
  (doseq [datapoint datapoints]
    (validate-datapoint timeseries datapoint))
  datapoints)

(defn add-timestamp
  [{:keys [timestamp] :as datapoint}]
  (cond-> datapoint
          (not timestamp) (assoc :timestamp (time/now-str))))
