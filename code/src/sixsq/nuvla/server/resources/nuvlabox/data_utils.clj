(ns sixsq.nuvla.server.resources.nuvlabox.data-utils
  (:require
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [environ.core :as env]
    [promesa.core :as p]
    [promesa.exec :as px]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.nuvlabox.status-utils :as status-utils]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))

(defn compute-bucket-availability
  "Compute bucket availability based on sum online and seconds in bucket."
  [sum-time-online seconds-in-bucket]
  (when (and sum-time-online (some-> seconds-in-bucket pos?))
    (let [avg (double (/ sum-time-online seconds-in-bucket))]
      (if (> avg 1.0) 1.0 avg))))

(defn compute-seconds-in-bucket
  [{start :timestamp} granularity-duration now]
  (let [end (time/plus start granularity-duration)
        end (if (time/after? end now) now end)]
    (time/time-between start end :seconds)))

(defn edge-at
  "Returns the edge if it was created before the given timestamp, and if sent
   a metric datapoint before the given timestamp, nil otherwise"
  [{:keys [created first-availability] :as _nuvlabox} timestamp]
  (and (some-> created
               (time/before? timestamp))
       (some-> first-availability
               :timestamp
               (time/before? timestamp))))

(defn bucket-end-time
  [bucket-start-time granularity-duration]
  (time/plus bucket-start-time
             granularity-duration))

(defn bucket-online-stats
  "Compute the amount of online seconds for a bucket, on a single nuvlabox."
  [{:keys [created] :as nuvlabox} {start :timestamp} granularity-duration now hits latest-online]
  (let [end             (time/plus start granularity-duration)
        end             (if (time/after? end now) now end)
        relevant-hits   (->> hits                           ;; hits are already filtered by nuvlaedge-id
                             (filter #(and (not (time/before? (:timestamp %) start))
                                           (time/before? (:timestamp %) end))))
        sum-time-online (cond
                          (empty? relevant-hits)
                          ;; perf opt - common case: no hits in the current bucket, just apply prev-online
                          (when (some? latest-online)
                            (if (= 1 latest-online)
                              (time/time-between start end :seconds)
                              0))

                          :else
                          (reduce
                            (fn [online-secs [{from :timestamp :keys [online]}
                                              {to :timestamp}]]
                              (when (some? online)
                                (cond
                                  (edge-at nuvlabox from)
                                  (+
                                    (or online-secs 0)
                                    (if (= 1 online)
                                      (time/time-between from to :seconds)
                                      0))
                                  (edge-at nuvlabox to)
                                  (+
                                    (or online-secs 0)
                                    (if (= 1 online)
                                      (time/time-between created to :seconds)
                                      0))
                                  :else
                                  nil)))
                            nil
                            (mapv vector
                                  (cons {:timestamp start
                                         :online    latest-online}
                                        relevant-hits)
                                  (conj (vec relevant-hits)
                                        {:timestamp end}))))]
    [sum-time-online (:online (or (last relevant-hits)
                                  {:online latest-online}))]))

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

(defn fetch-nuvlaboxes
  [{:keys [id cimi-filter additional-filter select request]}]
  (if id
    [(crud/retrieve-by-id id request)]
    (->> (crud/query
           (cond-> request
                   (or cimi-filter additional-filter)
                   (assoc :cimi-params
                          {:filter (parser/parse-cimi-filter
                                     (str/join " and " (remove nil? [cimi-filter additional-filter])))
                           :last   10000})
                   select
                   (assoc-in [:cimi-params :select] select)))
         :body
         :resources)))

(defn assoc-nuvlaedge-ids
  [params]
  (assoc params
    :nuvlaedge-ids (mapv :id (fetch-nuvlaboxes (assoc params :select ["id"])))))

(defn assoc-commissioned-nuvlaboxes
  [select params]
  (let [nuvlaboxes (fetch-nuvlaboxes (assoc params
                                       :additional-filter (str "state='" utils/state-commissioned "'")
                                       :select select))]
    (assoc params
      :nuvlaboxes nuvlaboxes
      :nuvlaedge-ids (mapv :id nuvlaboxes))))

(defn update-nuvlaboxes-dates
  [{:keys [nuvlaboxes] :as params}]
  (assoc params
    :nuvlaboxes
    (map #(update % :created time/date-from-str) nuvlaboxes)))

(defn commissioned?
  [nuvlabox]
  (= utils/state-commissioned (:state nuvlabox)))

(defn precompute-query-params
  [{:keys [predefined-aggregations granularity] :as query-opts}]
  (cond-> query-opts
          predefined-aggregations (assoc :granularity-duration (status-utils/granularity->duration granularity))))

(defn available-before?
  [{:keys [first-availability] :as _nuvlabox} timestamp]
  (some-> first-availability :timestamp (time/before? timestamp)))

(defn filter-available-before-period-end
  [{:keys [to nuvlaboxes] :as params}]
  (let [nuvlaboxes (filter #(available-before? % to) nuvlaboxes)]
    (assoc params
      :nuvlaboxes nuvlaboxes
      :nuvlaedge-ids (mapv :id nuvlaboxes))))

(defn assoc-first-availability
  [{:keys [nuvlaboxes nuvlaedge-ids] :as params}]
  (let [first-av   (doall (->> (utils/all-first-availability-status nuvlaedge-ids)
                               (group-by :nuvlaedge-id)))
        nuvlaboxes (->> nuvlaboxes
                        (mapv (fn [{:keys [id] :as nb}]
                                (assoc nb :first-availability
                                          (-> (first (get first-av id))
                                              (select-keys [:online :timestamp]))))))]
    (assoc params :nuvlaboxes nuvlaboxes)))

(def max-nuvlaboxes-count (env/env :max-nuvlaboxes-count 500))

(defn throw-too-many-nuvlaboxes
  [{:keys [nuvlaboxes] :as params}]
  (when (> (count nuvlaboxes) max-nuvlaboxes-count)
    (logu/log-and-throw-400 "Too many nuvlaedges"))
  params)

(defn timestamps->date
  [[query-opts resp]]
  [query-opts (update-resp-ts-data-points
                resp
                (fn [ts-data-point]
                  (update ts-data-point :timestamp time/date-from-str)))])

(defn timestamps->str
  [[query-opts resp]]
  [query-opts (update-resp-ts-data-points
                resp
                (fn [ts-data-point]
                  (update ts-data-point :timestamp time/to-str)))])

(defn assoc-latest-availability
  [[{:keys [nuvlaboxes nuvlaedge-ids] :as query-opts} resp]]
  (let [first-bucket-ts (some-> resp first :ts-data first :timestamp time/date-from-str)
        latest-av       (->> (utils/all-latest-availability-status nuvlaedge-ids first-bucket-ts)
                             (group-by :nuvlaedge-id))
        nuvlaboxes      (->> nuvlaboxes
                             (map (fn [{:keys [id] :as nb}]
                                    (assoc nb :latest-availability (select-keys (first (get latest-av id))
                                                                                [:online :timestamp])))))]
    [(assoc query-opts :nuvlaboxes nuvlaboxes) resp]))

(defn simplify-hits
  "Merge adjacent hits with same value for online."
  [hits]
  (reduce
    (fn [hits {:keys [online] :as hit}]
      (let [{prev-timestamp :timestamp prev-online :online} (peek hits)]
        (if (= prev-online online)
          (conj (pop hits) {:timestamp prev-timestamp :online online})
          (conj hits hit))))
    []
    hits))

(defn compute-nuvlabox-availability*
  "Compute availability for a single nuvlabox."
  [resp hits now granularity-duration {nuvlaedge-id :id :as nuvlabox} update-fn]
  (let [relevant-hits        (->> hits
                                  (filter #(= nuvlaedge-id (:nuvlaedge-id %)))
                                  (simplify-hits))
        prev-status          (-> nuvlabox :latest-availability :online)
        update-ts-data-point (fn [[ts-data latest-status] {:keys [timestamp] :as ts-data-point}]
                               (if (edge-at nuvlabox (bucket-end-time timestamp granularity-duration))
                                 (let [seconds-in-bucket (compute-seconds-in-bucket ts-data-point granularity-duration now)
                                       [sum-time-online latest-status]
                                       (bucket-online-stats nuvlabox ts-data-point granularity-duration now relevant-hits latest-status)]
                                   [(conj ts-data
                                          (update-fn
                                            ts-data-point
                                            (compute-bucket-availability sum-time-online seconds-in-bucket)))
                                    latest-status])
                                 [(conj ts-data ts-data-point) latest-status]))
        resp                 (update-resp-ts-data
                               resp
                               (fn [ts-data]
                                 (first (reduce update-ts-data-point
                                                [[] prev-status]
                                                ts-data))))]
    ;; we do not want to max out the cpu: to stop at 20% usage lets sleep for the same amount of time that it took to do the computation
    ;; (LockSupport/parkNanos (* 4 elapsed-nanos))
    resp))

(defn compute-nuvlabox-availability
  [[{:keys [predefined-aggregations granularity-duration nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [nuvlabox     (first nuvlaboxes)
          now          (time/now)
          hits         (->> (get-in resp [0 :hits])
                            (map #(update % :timestamp time/date-from-str))
                            reverse)
          update-av-fn (fn [ts-data-point availability]
                         (update ts-data-point
                                 :aggregations
                                 (fn [aggs]
                                   (assoc-in aggs [:avg-online :value] availability))))]
      [query-opts (compute-nuvlabox-availability* resp hits now granularity-duration nuvlabox update-av-fn)])
    [query-opts resp]))

(defn dissoc-hits
  [[query-opts resp]]
  [query-opts (update-in resp [0] dissoc :hits)])

(defn throw-custom-aggregations-not-exportable
  [{:keys [custom-es-aggregations]}]
  (when custom-es-aggregations
    (logu/log-and-throw-400 "Custom aggregations cannot be exported to csv format")))

(defn csv-export-fn
  [dimension-keys-fn meta-keys-fn metric-keys-fn data-fn]
  (fn [{:keys [resps] :as options}]
    (throw-custom-aggregations-not-exportable options)
    (utils/metrics-data->csv
      options
      (dimension-keys-fn options)
      (meta-keys-fn options)
      (metric-keys-fn options)
      data-fn
      (first resps))))

(defn csv-dimension-keys-fn
  []
  (fn [{:keys [raw predefined-aggregations datasets datasets-opts mode]}]
    (cond
      raw
      []

      predefined-aggregations
      (let [{group-by-field :group-by} (get datasets-opts (first datasets))
            dimension-keys (case mode
                             :single-edge-query
                             []
                             :multi-edge-query
                             [:nuvlaedge-count])]
        (cond-> dimension-keys
                (and predefined-aggregations group-by-field) (conj group-by-field))))))

(defn csv-meta-keys-fn
  []
  (fn [{:keys [mode predefined-aggregations raw]}]
    (cond
      raw (case mode
            :single-edge-query
            [:timestamp]
            :multi-edge-query
            [:timestamp :nuvlaedge-id])
      predefined-aggregations [:timestamp :doc-count])))

(defn availability-csv-metric-keys-fn
  []
  (fn [{:keys [predefined-aggregations raw datasets datasets-opts]}]
    (let [{:keys [response-aggs]} (get datasets-opts (first datasets))]
      (cond
        raw [:online]
        predefined-aggregations response-aggs))))

(defn availability-csv-data-fn
  []
  (fn [{:keys [predefined-aggregations raw]} {:keys [aggregations] :as data-point} metric-key]
    (cond
      raw
      (get data-point metric-key)

      predefined-aggregations
      (get-in aggregations [metric-key :value]))))

(defn availability-csv-export-fn
  []
  (csv-export-fn (csv-dimension-keys-fn)
                 (csv-meta-keys-fn)
                 (availability-csv-metric-keys-fn)
                 (availability-csv-data-fn)))

(defn telemetry-csv-metric-keys-fn
  [metric]
  (fn [{:keys [predefined-aggregations raw datasets datasets-opts resps]}]
    (let [{:keys [aggregations response-aggs]}
          (get datasets-opts (first datasets))]
      (cond
        raw
        (sort (keys (-> resps ffirst :ts-data first (get metric))))

        predefined-aggregations
        (or response-aggs (keys aggregations))))))

(defn telemetry-csv-data-fn
  [metric]
  (fn [{:keys [predefined-aggregations raw]}
       {:keys [aggregations] :as data-point} metric-key]
    (cond
      raw
      (get-in data-point [metric metric-key])

      predefined-aggregations
      (get-in aggregations [metric-key :value]))))

(defn telemetry-csv-export-fn
  [metric]
  (csv-export-fn (csv-dimension-keys-fn)
                 (csv-meta-keys-fn)
                 (telemetry-csv-metric-keys-fn metric)
                 (telemetry-csv-data-fn metric)))

(defn single-edge-datasets
  []
  {"availability-stats"      {:metric          "availability"
                              :pre-process-fn  (comp filter-available-before-period-end
                                                     assoc-first-availability
                                                     precompute-query-params
                                                     update-nuvlaboxes-dates
                                                     (partial assoc-commissioned-nuvlaboxes ["id" "created"]))
                              :post-process-fn (comp timestamps->str
                                                     dissoc-hits
                                                     compute-nuvlabox-availability
                                                     assoc-latest-availability
                                                     timestamps->date)
                              :response-aggs   [:avg-online]
                              :csv-export-fn   (availability-csv-export-fn)}
   "cpu-stats"               {:metric         "cpu"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :aggregations   {:avg-cpu-capacity    {:avg {:field :cpu.capacity}}
                                               :avg-cpu-load        {:avg {:field :cpu.load}}
                                               :avg-cpu-load-1      {:avg {:field :cpu.load-1}}
                                               :avg-cpu-load-5      {:avg {:field :cpu.load-5}}
                                               :context-switches    {:max {:field :cpu.context-switches}}
                                               :interrupts          {:max {:field :cpu.interrupts}}
                                               :software-interrupts {:max {:field :cpu.software-interrupts}}
                                               :system-calls        {:max {:field :cpu.system-calls}}}
                              :csv-export-fn  (telemetry-csv-export-fn :cpu)}
   "ram-stats"               {:metric         "ram"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :aggregations   {:avg-ram-capacity {:avg {:field :ram.capacity}}
                                               :avg-ram-used     {:avg {:field :ram.used}}}
                              :csv-export-fn  (telemetry-csv-export-fn :ram)}
   "disk-stats"              {:metric         "disk"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :group-by       :disk.device
                              :aggregations   {:avg-disk-capacity {:avg {:field :disk.capacity}}
                                               :avg-disk-used     {:avg {:field :disk.used}}}
                              :csv-export-fn  (telemetry-csv-export-fn :disk)}
   "network-stats"           {:metric         "network"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :group-by       :network.interface
                              :aggregations   {:bytes-received    {:max {:field :network.bytes-received}}
                                               :bytes-transmitted {:max {:field :network.bytes-transmitted}}}
                              :csv-export-fn  (telemetry-csv-export-fn :network)}
   "power-consumption-stats" {:metric         "power-consumption"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :group-by       :power-consumption.metric-name
                              :aggregations   {:energy-consumption {:max {:field :power-consumption.energy-consumption}}
                                               #_:unit                   #_{:first {:field :power-consumption.unit}}}
                              :csv-export-fn  (telemetry-csv-export-fn :power-consumption)}})

(defn edges-at
  "Returns the edges which were created before the given timestamp"
  [nuvlaboxes timestamp]
  (filter #(edge-at % timestamp) nuvlaboxes))

(defn expected-bucket-edge-ids
  [nuvlaboxes granularity-duration {:keys [timestamp] :as _ts-data-point}]
  (->> (bucket-end-time timestamp granularity-duration)
       (edges-at nuvlaboxes)
       (map :id)))

(def availability-executor (px/fixed-executor :parallelism
                                              (env/env :availability-executor-parallelism 1)))
(utils/add-executor-service-shutdown-hook
  availability-executor "availability computation executor")

(defn availabilities-sequential
  [{:keys [granularity-duration nuvlaboxes] :as _query-opts} resp now hits edge-bucket-update-fn]
  (let [nb-resps (map
                   (fn [{nuvlaedge-id :id :as nuvlabox}]
                     (compute-nuvlabox-availability*
                       resp
                       hits
                       now granularity-duration nuvlabox
                       (partial edge-bucket-update-fn nuvlaedge-id)))
                   nuvlaboxes)]
    (reduce
      (fn [resp nb-resp]
        (update-resp-ts-data-points
          resp
          (fn [{:keys [timestamp] :as ts-data-point}]
            (let [nb-bucket (-> (get-in nb-resp [0 :ts-data])
                                (->> (filter #(= timestamp (:timestamp %))))
                                first
                                (get-in [:aggregations :by-edge :buckets 0]))]
              (update-in ts-data-point
                         [:aggregations :by-edge :buckets]
                         (fn [buckets] (conj (or buckets []) nb-bucket)))))))
      resp
      nb-resps)))

(defn compute-nuvlaboxes-availabilities
  [[{:keys [predefined-aggregations] :as query-opts} resp]]
  (if predefined-aggregations
    (let [now                   (time/now)
          hits                  (->> (get-in resp [0 :hits])
                                     (map #(update % :timestamp time/date-from-str))
                                     reverse)
          edge-bucket-update-fn (fn [nuvlaedge-id ts-data-point availability]
                                  (assoc-in ts-data-point [:aggregations :by-edge :buckets]
                                            [{:key             nuvlaedge-id
                                              :edge-avg-online {:value availability}}]))]
      [query-opts (availabilities-sequential query-opts resp now hits edge-bucket-update-fn)])
    [query-opts resp]))

(defn compute-global-availability
  [[{:keys [predefined-aggregations] :as query-opts} resp]]
  [query-opts
   (cond->
     resp
     predefined-aggregations
     (update-resp-ts-data-point-aggs
       (fn [_ts-data-point {:keys [by-edge] :as aggs}]
         (let [avgs-count  (count (:buckets by-edge))
               avgs-online (keep #(-> % :edge-avg-online :value)
                                 (:buckets by-edge))]
           ;; here we can compute the average of the averages, because we give the same weight
           ;; to each edge (caveat: an edge created in the middle of a bucket will have the same
           ;; weight then an edge that was there since the beginning of the bucket).
           (assoc aggs :global-avg-online
                       {:value (if (seq avgs-online)
                                 (double (/ (apply + avgs-online)
                                            avgs-count))
                                 nil)})))))])

(defn add-edges-count
  [[{:keys [predefined-aggregations] :as query-opts} resp]]
  [query-opts
   (cond->
     resp
     predefined-aggregations
     (update-resp-ts-data-point-aggs
       (fn [_ts-data-point {:keys [by-edge] :as aggs}]
         (assoc aggs :edges-count {:value (count (:buckets by-edge))}))))])

(defn add-virtual-edge-number-by-status-fn
  [[{:keys [predefined-aggregations granularity-duration nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [edges-count (fn [timestamp] (count (edges-at nuvlaboxes (bucket-end-time timestamp granularity-duration))))]
      [query-opts
       (update-resp-ts-data-points
         resp
         (fn [{:keys [timestamp aggregations] :as ts-data-point}]
           (let [global-avg-online    (get-in aggregations [:global-avg-online :value])
                 edges-count-agg      (get-in aggregations [:edges-count :value])
                 n-virt-online-edges  (double (or (some->> global-avg-online (* edges-count-agg)) 0))
                 n-edges              (edges-count timestamp)
                 n-virt-offline-edges (- n-edges n-virt-online-edges)]
             (-> ts-data-point
                 (assoc-in [:aggregations :virtual-edges-online]
                           {:value n-virt-online-edges})
                 (assoc-in [:aggregations :virtual-edges-offline]
                           {:value n-virt-offline-edges})))))])
    [query-opts resp]))

(defn update-resp-edge-buckets
  [resp f]
  (update-resp-ts-data-point-aggs
    resp
    (fn [ts-data-point aggs]
      (update-in aggs [:by-edge :buckets]
                 (partial map (partial f ts-data-point))))))

(defn add-edge-names-fn
  [[{:keys [predefined-aggregations nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [edge-names-by-id (->> nuvlaboxes
                                (map (fn [{:keys [id name]}]
                                       [id name]))
                                (into {}))]
      [query-opts
       (update-resp-edge-buckets
         resp
         (fn [_ts-data-point {edge-id :key :as bucket}]
           (assoc bucket :name (get edge-names-by-id edge-id))))])
    [query-opts resp]))

(defn add-missing-edges-fn
  [[{:keys [predefined-aggregations granularity-duration nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (letfn [(update-buckets
              [ts-data-point buckets]
              (let [bucket-edge-ids  (set (map :key buckets))
                    missing-edge-ids (set/difference (set (expected-bucket-edge-ids nuvlaboxes granularity-duration ts-data-point))
                                                     bucket-edge-ids)]
                (concat buckets
                        (map (fn [missing-edge-id]
                               {:key       missing-edge-id
                                :doc_count 0})
                             missing-edge-ids))))]
      [query-opts
       (update-resp-ts-data-points
         resp
         (fn [ts-data-point]
           (update-in ts-data-point [:aggregations :by-edge :buckets]
                      (partial update-buckets ts-data-point))))])
    [query-opts resp]))

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

(defn multi-edge-datasets
  []
  (let [group-by-field     (fn [field aggs]
                             {:terms        {:field field}
                              :aggregations aggs})
        group-by-edge      (fn [aggs] (group-by-field :nuvlaedge-id aggs))
        group-by-device    (fn [aggs] (group-by-field :disk.device aggs))
        group-by-interface (fn [aggs] (group-by-field :network.interface aggs))]
    {"availability-stats"      {:metric          "availability"
                                :nuvlabox-filter "state='COMMISSIONED'"
                                :pre-process-fn  (comp filter-available-before-period-end
                                                       assoc-first-availability
                                                       precompute-query-params
                                                       update-nuvlaboxes-dates
                                                       throw-too-many-nuvlaboxes
                                                       (partial assoc-commissioned-nuvlaboxes ["id" "created"]))
                                :post-process-fn (comp timestamps->str
                                                       add-virtual-edge-number-by-status-fn
                                                       dissoc-hits
                                                       compute-global-availability
                                                       add-edges-count
                                                       add-missing-edges-fn
                                                       compute-nuvlaboxes-availabilities
                                                       assoc-latest-availability
                                                       timestamps->date)
                                :response-aggs   [:edges-count
                                                  :virtual-edges-online
                                                  :virtual-edges-offline]
                                :csv-export-fn   (availability-csv-export-fn)}
     "availability-by-edge"    {:metric          "availability"
                                :pre-process-fn  (comp filter-available-before-period-end
                                                       assoc-first-availability
                                                       precompute-query-params
                                                       update-nuvlaboxes-dates
                                                       throw-too-many-nuvlaboxes
                                                       (partial assoc-commissioned-nuvlaboxes ["id" "name" "created"]))
                                :post-process-fn (comp timestamps->str
                                                       dissoc-hits
                                                       compute-global-availability
                                                       add-edges-count
                                                       add-edge-names-fn
                                                       add-missing-edges-fn
                                                       compute-nuvlaboxes-availabilities
                                                       assoc-latest-availability
                                                       timestamps->date)
                                :response-aggs   [:edges-count
                                                  :by-edge
                                                  :global-avg-online]}
     "cpu-stats"               {:metric         "cpu"
                                :pre-process-fn assoc-nuvlaedge-ids
                                :aggregations   {:avg-cpu-capacity        (group-by-edge {:by-edge {:avg {:field :cpu.capacity}}})
                                                 :avg-cpu-load            (group-by-edge {:by-edge {:avg {:field :cpu.load}}})
                                                 :avg-cpu-load-1          (group-by-edge {:by-edge {:avg {:field :cpu.load-1}}})
                                                 :avg-cpu-load-5          (group-by-edge {:by-edge {:avg {:field :cpu.load-5}}})
                                                 :context-switches        (group-by-edge {:by-edge {:max {:field :cpu.context-switches}}})
                                                 :interrupts              (group-by-edge {:by-edge {:max {:field :cpu.interrupts}}})
                                                 :software-interrupts     (group-by-edge {:by-edge {:max {:field :cpu.software-interrupts}}})
                                                 :system-calls            (group-by-edge {:by-edge {:max {:field :cpu.system-calls}}})
                                                 :sum-avg-cpu-capacity    {:sum_bucket {:buckets_path :avg-cpu-capacity>by-edge}}
                                                 :sum-avg-cpu-load        {:sum_bucket {:buckets_path :avg-cpu-load>by-edge}}
                                                 :sum-avg-cpu-load-1      {:sum_bucket {:buckets_path :avg-cpu-load-1>by-edge}}
                                                 :sum-avg-cpu-load-5      {:sum_bucket {:buckets_path :avg-cpu-load-5>by-edge}}
                                                 :sum-context-switches    {:sum_bucket {:buckets_path :context-switches>by-edge}}
                                                 :sum-interrupts          {:sum_bucket {:buckets_path :interrupts>by-edge}}
                                                 :sum-software-interrupts {:sum_bucket {:buckets_path :software-interrupts>by-edge}}
                                                 :sum-system-calls        {:sum_bucket {:buckets_path :system-calls>by-edge}}}
                                :response-aggs  [:sum-avg-cpu-capacity :sum-avg-cpu-load :sum-avg-cpu-load-1 :sum-avg-cpu-load-5
                                                 :sum-context-switches :sum-interrupts :sum-software-interrupts :sum-system-calls]
                                :csv-export-fn  (telemetry-csv-export-fn :cpu)}
     "ram-stats"               {:metric         "ram"
                                :pre-process-fn assoc-nuvlaedge-ids
                                :aggregations   {:avg-ram-capacity     (group-by-edge {:by-edge {:avg {:field :ram.capacity}}})
                                                 :avg-ram-used         (group-by-edge {:by-edge {:avg {:field :ram.used}}})
                                                 :sum-avg-ram-capacity {:sum_bucket {:buckets_path :avg-ram-capacity>by-edge}}
                                                 :sum-avg-ram-used     {:sum_bucket {:buckets_path :avg-ram-used>by-edge}}}
                                :response-aggs  [:sum-avg-ram-capacity :sum-avg-ram-used]
                                :csv-export-fn  (telemetry-csv-export-fn :ram)}
     "disk-stats"              {:metric         "disk"
                                :pre-process-fn assoc-nuvlaedge-ids
                                :aggregations   {:avg-disk-capacity     (group-by-edge
                                                                          {:by-edge                 (group-by-device
                                                                                                      {:by-device {:avg {:field :disk.capacity}}})
                                                                           :total-avg-edge-capacity {:sum_bucket {:buckets_path :by-edge>by-device}}})
                                                 :avg-disk-used         (group-by-edge
                                                                          {:by-edge                      (group-by-device
                                                                                                           {:by-device {:avg {:field :disk.used}}})
                                                                           :total-avg-edge-used-capacity {:sum_bucket {:buckets_path :by-edge>by-device}}})
                                                 :sum-avg-disk-capacity {:sum_bucket {:buckets_path :avg-disk-capacity>total-avg-edge-capacity}}
                                                 :sum-avg-disk-used     {:sum_bucket {:buckets_path :avg-disk-used>total-avg-edge-used-capacity}}}
                                :response-aggs  [:sum-avg-disk-capacity :sum-avg-disk-used]
                                :csv-export-fn  (telemetry-csv-export-fn :disk)}
     "network-stats"           {:metric         "network"
                                :pre-process-fn assoc-nuvlaedge-ids
                                :aggregations   {:bytes-received        (group-by-edge
                                                                          {:by-edge                   (group-by-interface
                                                                                                        {:by-interface {:max {:field :network.bytes-received}}})
                                                                           :total-edge-bytes-received {:sum_bucket {:buckets_path :by-edge>by-interface}}})
                                                 :bytes-transmitted     (group-by-edge
                                                                          {:by-edge                      (group-by-interface
                                                                                                           {:by-interface {:max {:field :network.bytes-transmitted}}})
                                                                           :total-edge-bytes-transmitted {:sum_bucket {:buckets_path :by-edge>by-interface}}})
                                                 :sum-bytes-received    {:sum_bucket {:buckets_path :bytes-received>total-edge-bytes-received}}
                                                 :sum-bytes-transmitted {:sum_bucket {:buckets_path :bytes-transmitted>total-edge-bytes-transmitted}}}
                                :response-aggs  [:sum-bytes-received :sum-bytes-transmitted]
                                :csv-export-fn  (telemetry-csv-export-fn :network)}
     "power-consumption-stats" {:metric         "power-consumption"
                                :pre-process-fn assoc-nuvlaedge-ids
                                :group-by       :power-consumption.metric-name
                                :aggregations   {:energy-consumption     (group-by-edge {:by-edge {:max {:field :power-consumption.energy-consumption}}})
                                                 #_:unit                   #_{:first {:field :power-consumption.unit}}
                                                 :sum-energy-consumption {:sum_bucket {:buckets_path :energy-consumption>by-edge}}}
                                :response-aggs  [:sum-energy-consumption]
                                :csv-export-fn  (telemetry-csv-export-fn :power-consumption)}}))

(defn parse-params
  [{:keys [uuid dataset from to granularity custom-es-aggregations] :as params}]
  (let [datasets                (if (coll? dataset) dataset [dataset])
        raw                     (= "raw" granularity)
        predefined-aggregations (not (or raw custom-es-aggregations))
        custom-es-aggregations  (cond-> custom-es-aggregations
                                        (string? custom-es-aggregations)
                                        json/read-str)]
    (-> params
        (assoc :datasets datasets)
        (assoc :from (time/date-from-str from))
        (assoc :to (time/date-from-str to))
        (cond->
          uuid (assoc :id (u/resource-id "nuvlabox" uuid))
          raw (assoc :raw true)
          predefined-aggregations (assoc :predefined-aggregations true)
          custom-es-aggregations (assoc :custom-es-aggregations custom-es-aggregations)))))

(defn throw-mandatory-dataset-parameter
  [{:keys [datasets] :as params}]
  (when-not (seq datasets) (logu/log-and-throw-400 "dataset parameter is mandatory"))
  params)

(defn throw-mandatory-from-to-parameters
  [{:keys [from to] :as params}]
  (when-not from
    (logu/log-and-throw-400 (str "from parameter is mandatory, with format " time/iso8601-format)))
  (when-not to
    (logu/log-and-throw-400 (str "to parameter is mandatory, with format " time/iso8601-format)))
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
    (let [max-n-buckets utils/max-data-points
          n-buckets     (.dividedBy (time/duration from to)
                                    (status-utils/granularity->duration granularity))]
      (when (> n-buckets max-n-buckets)
        (logu/log-and-throw-400 "too many data points requested. Please restrict the time interval or increase the time granularity."))))
  params)

(defn throw-response-format-not-supported
  [{:keys [accept-header] :as params}]
  (when (and (some? accept-header) (not (#{"application/json" "text/csv"} accept-header)))
    (logu/log-and-throw-400 (str "format not supported: " accept-header)))
  params)

(defn assoc-base-query-opts
  [{:keys [predefined-aggregations granularity filter] :as params} request]
  (assoc params
    :base-query-opts
    (-> (select-keys params [:id :from :to :granularity
                             :raw :custom-es-aggregations :predefined-aggregations
                             :mode])
        (assoc :request request)
        (cond->
          filter
          (assoc :cimi-filter filter)
          predefined-aggregations
          (assoc :ts-interval (status-utils/granularity->ts-interval granularity))))))

(defn assoc-datasets-opts
  [{:keys [mode] :as params}]
  (assoc params
    :datasets-opts
    (case mode
      :single-edge-query (single-edge-datasets)
      :multi-edge-query (multi-edge-datasets))))

(defn throw-unknown-datasets
  [{:keys [datasets datasets-opts] :as params}]
  (when-not (every? (set (keys datasets-opts)) datasets)
    (logu/log-and-throw-400 (str "unknown datasets: "
                                 (str/join "," (sort (set/difference (set datasets)
                                                                     (set (keys datasets-opts))))))))
  params)

(defn throw-csv-multi-dataset
  [{:keys [datasets accept-header] :as params}]
  (when (and (= "text/csv" accept-header) (not= 1 (count datasets)))
    (logu/log-and-throw-400 (str "exactly one dataset must be specified with accept header 'text/csv'")))
  params)

(defn run-query
  [base-query-opts datasets-opts dataset-key]
  (let [{:keys [metric pre-process-fn post-process-fn] :as dataset-opts} (get datasets-opts dataset-key)
        {:keys [predefined-aggregations] :as query-opts} (merge base-query-opts dataset-opts)
        query-fn   (case metric
                     "availability" utils/query-availability
                     utils/query-metrics)
        query-opts (if pre-process-fn (doall (pre-process-fn query-opts)) query-opts)]
    (cond->> (doall @(future (query-fn query-opts)))
             post-process-fn ((fn [resp] (doall (second (post-process-fn [query-opts resp])))))
             predefined-aggregations (keep-response-aggs-only query-opts))))

(defn run-queries
  [{:keys [datasets base-query-opts datasets-opts] :as params}]
  (assoc params
    :resps
    (map (partial run-query base-query-opts datasets-opts) datasets)))

(defn json-data-response
  [{:keys [datasets resps]}]
  (r/json-response (zipmap datasets resps)))

(defn csv-response
  [{:keys [datasets datasets-opts] :as options}]
  (let [{:keys [csv-export-fn]} (get datasets-opts (first datasets))]
    (when-not csv-export-fn
      (logu/log-and-throw-400 (str "csv export not supported for dataset " (first datasets))))
    (r/csv-response "export.csv" (csv-export-fn options))))

(defn send-data-response
  [{:keys [accept-header] :as options}]
  (case accept-header
    (nil "application/json")                                ; by default return a json response
    (json-data-response options)
    "text/csv"
    (csv-response options)))

(defn query-data
  [params request]
  (-> params
      (parse-params)
      (throw-mandatory-dataset-parameter)
      (throw-mandatory-from-to-parameters)
      (throw-from-not-before-to)
      (throw-mandatory-granularity-parameter)
      (throw-too-many-data-points)
      (throw-custom-es-aggregations-checks)
      (throw-response-format-not-supported)
      (assoc-base-query-opts request)
      (assoc-datasets-opts)
      (throw-unknown-datasets)
      (throw-csv-multi-dataset)
      (run-queries)
      (send-data-response)))

(def query-data-executor (px/fixed-executor :parallelism
                                            (env/env :query-data-executor-parallelism 1)))
(utils/add-executor-service-shutdown-hook
  query-data-executor "query data executor")

(def running-query-data (atom 0))
(def requesting-query-data (atom 0))

(def query-data-max-attempts (env/env :query-data-max-attempts 50))
(def query-data-max-time (env/env :query-data-max-time 25000))

(defn gated-query-data
  "Only allow one call to query-data on availability of multiple edges at a time.
   Allow max 4 additional requests to wait at most 5 seconds to get
   access to computation."
  [{:keys [mode dataset] :as params} request]
  (let [datasets (if (coll? dataset) dataset [dataset])]
    (if (and (= :multi-edge-query mode)
             (some #{"availability-stats" "availability-by-edge"} datasets))
      (if (> @requesting-query-data 4)
        (logu/log-and-throw 503 "Server too busy")
        ;; retry for up to 5 seconds (or QUERY_DATA_MAX_ATTEMPTS * 100ms)
        (try
          (swap! requesting-query-data inc)
          (loop [remaining-attempts query-data-max-attempts]
            (if (zero? remaining-attempts)
              (logu/log-and-throw 504 "Timed out waiting for query slot")
              (if (= @running-query-data 0)
                (do
                  (swap! running-query-data inc)
                  (utils/exec-with-timeout!
                    query-data-executor
                    (fn []
                      (try
                        (query-data params request)
                        (finally
                          (swap! running-query-data dec))))
                    ;; allow 25 seconds max (or QUERY_DATA_MAX_TIME)
                    query-data-max-time
                    "data query timed out"))
                (do
                  ;; wait 100ms and retry
                  @(p/delay 100)
                  (recur (dec remaining-attempts))))))
          (finally
            (swap! requesting-query-data dec))))
      ;; let non-availability queries go through
      (query-data params request))))
