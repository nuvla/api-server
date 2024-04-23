(ns sixsq.nuvla.server.resources.timeseries.data-utils
  (:require [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as str]
            [ring.middleware.accept :refer [wrap-accept]]
            [sixsq.nuvla.auth.acl-resource :as a]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.timeseries.utils :as utils]
            [sixsq.nuvla.server.util.log :as logu]
            [sixsq.nuvla.server.util.response :as r]
            [sixsq.nuvla.server.util.time :as time]))

(def max-data-points 200)

(defn update-resp-ts-data
  [resp f]
  (-> resp
      vec
      (update-in [0 :ts-data] (comp vec f))))

(defn update-resp-ts-data-points
  [resp f]
  (update-resp-ts-data
    resp
    (fn [ts-data]
      (mapv f ts-data))))

(defn update-resp-ts-data-point-aggs
  [resp f]
  (update-resp-ts-data-points
    resp
    (fn [ts-data-point]
      (update ts-data-point :aggregations (partial f ts-data-point)))))

(defn granularity->duration
  "Converts from a string of the form <n>-<units> to java.time duration"
  [granularity]
  (let [[_ n unit] (re-matches #"(.*)-(.*)" (name granularity))]
    (try
      (time/duration (Integer/parseInt n) (keyword unit))
      (catch Exception _
        (logu/log-and-throw-400 (str "unrecognized value for granularity " granularity))))))

(defn keep-response-aggs-only
  [{:keys [predefined-aggregations response-aggs] :as _query-opts} resp]
  (cond->
    resp
    predefined-aggregations
    (update-resp-ts-data-point-aggs
      (fn [_ts-data-point aggs]
        (if response-aggs
          (select-keys aggs response-aggs)
          aggs)))))

(defn parse-params
  [{:keys [query from to granularity custom-es-aggregations] :as params}
   {:keys [accept] :as _request}]
  (let [queries                 (if (coll? query) query [query])
        raw                     (= "raw" granularity)
        predefined-aggregations (not (or raw custom-es-aggregations))
        custom-es-aggregations  (cond-> custom-es-aggregations
                                        (string? custom-es-aggregations)
                                        json/read-str)]
    (-> params
        (assoc :mime-type (:mime accept))
        (assoc :queries queries)
        (assoc :from (time/parse-date from))
        (assoc :to (time/parse-date to))
        (cond->
          raw (assoc :raw true)
          predefined-aggregations (assoc :predefined-aggregations true)
          custom-es-aggregations (assoc :custom-es-aggregations custom-es-aggregations)))))

(defn throw-response-format-not-supported
  [{:keys [mime-type] :as params}]
  (when-not mime-type
    (logu/log-and-throw-400 406 "Not Acceptable"))
  params)

(defn throw-mandatory-query-parameter
  [{:keys [queries] :as params}]
  (when-not (seq queries) (logu/log-and-throw-400 "query parameter is mandatory"))
  params)

(defn throw-mandatory-from-to-parameters
  [{:keys [from to] :as params}]
  (when-not from
    (logu/log-and-throw-400 (str "from parameter is mandatory, with format iso8601 (uuuu-MM-dd'T'HH:mm:ss[.SSS]Z)")))
  (when-not to
    (logu/log-and-throw-400 (str "to parameter is mandatory, with format iso8601 (uuuu-MM-dd'T'HH:mm:ss[.SSS]Z)")))
  params)

(defn throw-from-not-before-to
  [{:keys [from to] :as params}]
  (when-not (time/before? from to)
    (logu/log-and-throw-400 "from must be before to"))
  params)

(defn throw-mandatory-granularity-parameter
  [{:keys [raw granularity custom-es-aggregations] :as params}]
  (when (and (not raw) (not custom-es-aggregations) (empty? granularity))
    (logu/log-and-throw-400 "granularity parameter is mandatory"))
  params)

(defn throw-custom-es-aggregations-checks
  [{:keys [custom-es-aggregations granularity] :as params}]
  (when custom-es-aggregations
    (when granularity
      (logu/log-and-throw-400 "when custom-es-aggregations is specified, granularity parameter must be omitted")))
  params)

(defn throw-too-many-data-points
  [{:keys [from to granularity predefined-aggregations] :as params}]
  (when predefined-aggregations
    (let [max-n-buckets max-data-points
          n-buckets     (.dividedBy (time/duration from to)
                                    (granularity->duration granularity))]
      (when (> n-buckets max-n-buckets)
        (logu/log-and-throw-400 "too many data points requested. Please restrict the time interval or increase the time granularity."))))
  params)

(defn granularity->ts-interval
  "Converts from a string of the form <n>-<units> to an ElasticSearch interval string"
  [granularity]
  (let [[_ n unit] (re-matches #"(.*)-(.*)" (name granularity))]
    (str n (case unit
             "seconds" "s"
             "minutes" "m"
             "hours" "h"
             "days" "d"
             "weeks" "d"
             "months" "M"
             (logu/log-and-throw-400 (str "unrecognized value for granularity " granularity))))))

(defn assoc-request
  [params request]
  (assoc params :request request))

(defn assoc-cimi-filter
  [{:keys [filter] :as params}]
  (cond-> params filter (assoc :cimi-filter filter)))

(defn assoc-ts-interval
  [{:keys [predefined-aggregations granularity] :as params}]
  (cond-> params
          predefined-aggregations
          (assoc :ts-interval (granularity->ts-interval granularity))))

(defn throw-unknown-queries
  [{:keys [queries query-specs] :as params}]
  (when-not (every? (set (keys query-specs)) queries)
    (logu/log-and-throw-400 (str "unknown queries: "
                                 (str/join "," (sort (set/difference (set queries)
                                                                     (set (keys query-specs))))))))
  params)

(defn throw-csv-multi-query
  [{:keys [queries mime-type] :as params}]
  (when (and (= "text/csv" mime-type) (not= 1 (count queries)))
    (logu/log-and-throw-400 (str "exactly one query must be specified with accept header 'text/csv'")))
  params)

(defn run-query
  [params query-specs query-key]
  (let [{:keys [pre-process-fn query-fn post-process-fn] :as query-spec} (get query-specs query-key)
        {:keys [predefined-aggregations] :as query-opts} (merge params query-spec)
        query-opts (if pre-process-fn (doall (pre-process-fn query-opts)) query-opts)]
    (cond->> (doall (query-fn query-opts))
             post-process-fn ((fn [resp] (doall (second (post-process-fn [query-opts resp])))))
             predefined-aggregations (keep-response-aggs-only query-opts))))

(defn run-queries
  [{:keys [queries query-specs] :as params}]
  (assoc params
    :resps
    (map (partial run-query params query-specs) queries)))

(defn json-data-response
  [{:keys [queries resps]}]
  (r/json-response (zipmap queries resps)))

(defn csv-response
  [{:keys [queries query-specs] :as options}]
  (let [{:keys [csv-export-fn]} (get query-specs (first queries))]
    (when-not csv-export-fn
      (logu/log-and-throw-400 (str "csv export not supported for query " (first queries))))
    (r/csv-response "export.csv" (csv-export-fn options))))

(defn send-data-response
  [{:keys [mime-type] :as options}]
  (case mime-type
    "application/json"
    (json-data-response options)
    "text/csv"
    (csv-response options)))

(defn query-data
  [params request]
  (-> params
      (parse-params request)
      (throw-response-format-not-supported)
      (throw-mandatory-query-parameter)
      (throw-mandatory-from-to-parameters)
      (throw-from-not-before-to)
      (throw-mandatory-granularity-parameter)
      (throw-too-many-data-points)
      (throw-custom-es-aggregations-checks)
      (assoc-request request)
      (assoc-cimi-filter)
      (assoc-ts-interval)
      (throw-unknown-queries)
      (throw-csv-multi-query)
      (run-queries)
      (send-data-response)))

(defn wrap-query-data-accept
  [handler]
  (wrap-accept handler
               {:mime ["application/json" :qs 1
                       "text/csv" :qs 0.5]}))

(defn assoc-timeseries
  [{uuid :uuid :as params} request]
  (let [id               (str "timeseries/" uuid)
        timeseries-index (utils/resource-id->timeseries-index id)
        timeseries       (-> (crud/retrieve-by-id-as-admin id)
                             (a/throw-cannot-manage request))]
    (assoc params
      :timeseries-index timeseries-index
      :timeseries timeseries)))

(defn ->resp-dimensions
  [{:keys [timeseries dimensions-filters]}]
  (->> (for [{:keys [field-name]} (:dimensions timeseries)]
         (let [v (get dimensions-filters field-name)]
           (cond
             (nil? v)
             {field-name "all"}

             (= (count v) 1)
             {field-name (first v)}

             (pos? (count v))
             {field-name {:count (count v)}})))
       (into {})))

(defn ->predefined-aggregations-resp
  [{:keys [aggregations ->resp-dimensions-fn] group-by-field :group-by :as params} resp]
  (let [ts-data (fn [tsds-stats]
                  (map
                    (fn [{:keys [key_as_string doc_count] :as bucket}]
                      {:timestamp    key_as_string
                       :doc-count    doc_count
                       :aggregations (->> (keys aggregations)
                                          (map keyword)
                                          (select-keys bucket))})
                    (:buckets tsds-stats)))
        hits    (second resp)]
    (if group-by-field
      (for [{:keys [key tsds-stats]} (get-in resp [0 :aggregations :by-field :buckets])]
        (cond->
          {:dimensions (assoc (->resp-dimensions-fn params) group-by-field key)
           :ts-data    (ts-data tsds-stats)}
          (seq hits) (assoc :hits hits)))
      [(cond->
         {:dimensions (->resp-dimensions-fn params)
          :ts-data    (ts-data (get-in resp [0 :aggregations :tsds-stats]))}
         (seq hits) (assoc :hits hits))])))

(defn ->custom-es-aggregations-resp
  [{:keys [->resp-dimensions-fn] :as params} resp]
  (let [ts-data (fn [tsds-stats]
                  (map
                    (fn [{:keys [key_as_string doc_count] :as bucket}]
                      {:timestamp    key_as_string
                       :doc-count    doc_count
                       :aggregations (dissoc bucket :key_as_string :key :doc_count)})
                    (:buckets tsds-stats)))]
    [(merge {:dimensions (->resp-dimensions-fn params)}
            (into {} (for [agg-key (keys (get-in resp [0 :aggregations]))]
                       [agg-key (ts-data (get-in resp [0 :aggregations agg-key]))])))]))

(defn ->raw-resp
  [{:keys [->resp-dimensions-fn] :as params} resp]
  (let [hits (second resp)]
    [{:dimensions (->resp-dimensions-fn params)
      :ts-data    (sort-by :timestamp hits)}]))

(defn ->ts-query-resp
  [{:keys [predefined-aggregations custom-es-aggregations raw] :as params} resp]
  (cond
    predefined-aggregations
    (->predefined-aggregations-resp params resp)

    raw
    (->raw-resp params resp)

    custom-es-aggregations
    (->custom-es-aggregations-resp params resp)))

(defn build-aggregations-clause
  [{:keys [predefined-aggregations raw custom-es-aggregations from to ts-interval aggregations] group-by-field :group-by}]
  (cond
    raw
    {}                                                      ;; send an empty :tsds-aggregation to avoid acl checks. TODO: find a cleaner way

    predefined-aggregations
    (let [tsds-aggregations {:tsds-stats
                             {:date_histogram
                              {:field           "@timestamp"
                               :fixed_interval  ts-interval
                               :min_doc_count   0
                               :extended_bounds {:min (time/to-str from)
                                                 :max (time/to-str to)}}
                              :aggregations (or aggregations {})}}]
      (if group-by-field
        {:aggregations
         {:by-field
          {:terms        {:field group-by-field}
           :aggregations tsds-aggregations}}}
        {:aggregations tsds-aggregations}))

    custom-es-aggregations
    {:aggregations custom-es-aggregations}))

(defn dimension-filter->cimi-filter
  [[dimension values]]
  (str dimension "=[" (str/join " " (map #(str "'" % "'") values)) "]"))

(defn build-ts-query [{:keys [last dimensions-filters from to additional-filters orderby] :as options}]
  (let [time-range-filter  (str "@timestamp>'" (time/to-str from) "'"
                                " and "
                                "@timestamp<'" (time/to-str to) "'")
        aggregation-clause (build-aggregations-clause options)]
    (cond->
      {:cimi-params (cond->
                      {:last (or last 0)
                       :filter
                       (parser/parse-cimi-filter
                         (str "("
                              (apply str
                                     (interpose " and "
                                                (concat [time-range-filter]
                                                        (map dimension-filter->cimi-filter dimensions-filters)
                                                        additional-filters)))
                              ")"))}
                      orderby (assoc :orderby orderby))}
      aggregation-clause
      (assoc :params {:tsds-aggregation (json/write-str aggregation-clause)}))))

(defn build-query [{:keys [raw] :as options}]
  (-> (build-ts-query (cond-> options
                              raw (assoc :last max-data-points)))
      (assoc :no-prefix true)))

(defn generic-query-fn
  [{:keys [timeseries-index] :as params}]
  (->> (build-query params)
       (crud/query-as-admin timeseries-index)
       (->ts-query-resp (assoc params :->resp-dimensions-fn ->resp-dimensions))))

(defmulti ts-query->query-spec (fn [{:keys [query-type]}] query-type))

(defmethod ts-query->query-spec :default
  [{:keys [query-type]}]
  (logu/log-and-throw-400 (str "unrecognized query type " query-type)))

(defn parse-aggregations
  [aggregations]
  (->> aggregations
       (map (fn [{:keys [aggregation-name aggregation-type field-name]}]
              [aggregation-name {(keyword aggregation-type) {:field field-name}}]))
       (into {})))

(defmethod ts-query->query-spec "standard"
  [{:keys [query] :as _ts-query}]
  {:query-fn     generic-query-fn
   :aggregations (some-> query :aggregations parse-aggregations)
   ; :csv-export-fn  (telemetry-csv-export-fn :cpu)
   })

(defmethod ts-query->query-spec "custom-es-query"
  [{:keys [custom-es-query] :as _ts-query}]
  {:query-fn     generic-query-fn
   :aggregations (some-> custom-es-query :aggregations)
   ; :csv-export-fn  (telemetry-csv-export-fn :cpu)
   })

(defn assoc-query-specs
  [{:keys [timeseries] :as params}]
  (let [query-specs (-> (get timeseries :queries)
                        (->> (group-by :query-name))
                        (update-vals (comp ts-query->query-spec first)))]
    (cond-> params
            query-specs (assoc :query-specs query-specs))))

(defn parse-dimension-filter
  [s]
  (let [[_ dimension value] (re-matches #"(.*)=(.*)" s)]
    [dimension value]))

(defn assoc-dimensions-filters
  [{:keys [dimension-filter] :as params}]
  (let [dimension-filter   (when dimension-filter
                             (if (coll? dimension-filter) dimension-filter [dimension-filter]))
        dimensions-filters (-> (->> dimension-filter
                                    (map parse-dimension-filter)
                                    (group-by first))
                               (update-vals #(map second %)))]
    (cond-> params
            dimensions-filters (assoc :dimensions-filters dimensions-filters))))

(defn throw-invalid-dimensions
  [{:keys [dimensions-filters] {:keys [dimensions]} :timeseries :as params}]
  (let [dimensions-filters-keys (set (keys dimensions-filters))
        dimensions-field-names  (set (map :field-name dimensions))]
    (when (seq dimensions-filters-keys)
      (when-not (set/subset? dimensions-filters-keys dimensions-field-names)
        (throw (r/ex-response (str "invalid dimensions: "
                                   (str/join "," (set/difference dimensions-filters-keys dimensions-field-names)))
                              400)))))
  params)

(defn generic-ts-query-data
  [params request]
  (-> params
      (assoc-timeseries request)
      (assoc-query-specs)
      (assoc-dimensions-filters)
      (throw-invalid-dimensions)
      (query-data request)))

(defn wrapped-query-data
  [params request]
  (let [query-data (wrap-query-data-accept (partial generic-ts-query-data params))]
    (query-data request)))
