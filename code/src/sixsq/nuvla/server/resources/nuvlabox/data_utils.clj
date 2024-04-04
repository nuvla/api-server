(ns sixsq.nuvla.server.resources.nuvlabox.data-utils
  (:require
    [clojure.data.csv :as csv]
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [promesa.core :as p]
    [promesa.exec :as px]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.ts-nuvlaedge-availability :as ts-nuvlaedge-availability]
    [sixsq.nuvla.server.resources.ts-nuvlaedge-telemetry :as ts-nuvlaedge-telemetry]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time])
  (:import
    (java.io StringWriter)
    (java.text DecimalFormat DecimalFormatSymbols)
    (java.util Locale)
    (java.util.concurrent ExecutionException TimeoutException)))

(def max-data-points 200)
(def running-query-data (atom 0))
(def requesting-query-data (atom 0))
(def query-data-max-attempts (env/env :query-data-max-attempts 50))
(def query-data-max-time (env/env :query-data-max-time 25000))

(def first-availability-query-timeout (env/env :first-availability-query-timeout 10000))
(def latest-availability-query-timeout (env/env :latest-availability-query-timeout 10000))
(def max-nuvlaboxes-count (env/env :max-nuvlaboxes-count 10000))

(defn add-jvm-shutdown-hook
  [f]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable f)))

(defn add-executor-service-shutdown-hook
  ([executor]
   (add-executor-service-shutdown-hook executor nil))
  ([executor executor-name]
   (add-jvm-shutdown-hook
     (fn []
       (when executor-name
         (log/info "Executor " executor-name " shutting down"))
       (px/shutdown-now! executor)
       (when executor-name
         (log/info "Executor " executor-name " shutdown completed"))))))

(def timeout-executor (px/scheduled-executor :parallelism (env/env :timeout-executor-parallelism 1)))
(add-executor-service-shutdown-hook timeout-executor "timeout executor")

(def availability-executor (px/fixed-executor :parallelism (env/env :availability-executor-parallelism 1)))
(add-executor-service-shutdown-hook availability-executor "availability computation executor")

(def query-data-executor (px/fixed-executor :parallelism (env/env :query-data-executor-parallelism 1)))
(add-executor-service-shutdown-hook query-data-executor "query data executor")

(defn query-with-timeout
  [resource-type timeout query]
  ;; just send the timeout to ES for now, and run the query in the current thread
  (crud/query-native resource-type (assoc query :timeout (str timeout "ms"))))

(defn exec-with-timeout!
  ([f timeout]
   (exec-with-timeout! f timeout "Operation timed out"))
  ([f timeout timeout-msg]
   (exec-with-timeout! nil f timeout timeout-msg))
  ([executor f timeout timeout-msg]
   (let [int-atom (atom false)
         task     (-> (px/submit! executor (fn [] (f int-atom)))
                      (p/timeout timeout ::p/default timeout-executor))]
     (try @task
          (catch ExecutionException ee
            (let [ec (.getCause ee)]
              (if (= (type ec) TimeoutException)
                (do
                  (p/cancel! task)
                  (reset! int-atom true)
                  (logu/log-and-throw 504 timeout-msg))
                (throw ec))))))))

(defn str-key->date
  [k m]
  (update m k time/date-from-str))

(defmulti nuvlabox-status->metric-data (fn [_ _nb metric _] metric))

(defmethod nuvlabox-status->metric-data :default
  [{:keys [resources]} _nb metric _from-telemetry]
  (when-let [metric-data (get resources metric)]
    [{metric metric-data}]))

(defmethod nuvlabox-status->metric-data :cpu
  [{{:keys [cpu]} :resources} _nb _metric _from-telemetry]
  (when cpu
    [{:cpu (select-keys cpu
                        [:capacity
                         :load
                         :load-1
                         :load-5
                         :context-switches
                         :interrupts
                         :software-interrupts
                         :system-calls])}]))

(defmethod nuvlabox-status->metric-data :ram
  [{{:keys [ram]} :resources} _nb _metric _from-telemetry]
  (when ram
    [{:ram (select-keys ram [:capacity :used])}]))

(defmethod nuvlabox-status->metric-data :disk
  [{{:keys [disks]} :resources} _nb _metric _from-telemetry]
  (when (seq disks)
    (mapv (fn [data] {:disk (select-keys data [:device :capacity :used])}) disks)))

(defmethod nuvlabox-status->metric-data :network
  [{{:keys [net-stats]} :resources} _nb _metric _from-telemetry]
  (when (seq net-stats)
    (mapv (fn [data] {:network (select-keys data [:interface :bytes-transmitted :bytes-received])}) net-stats)))

(defmethod nuvlabox-status->metric-data :power-consumption
  [{{:keys [power-consumption]} :resources} _nb _metric _from-telemetry]
  (when (seq power-consumption)
    (mapv (fn [data] {:power-consumption (select-keys data [:metric-name :energy-consumption :unit])}) power-consumption)))

(defn nuvlabox-status->bulk-insert-metrics-request-body
  [{:keys [parent current-time] :as nuvlabox-status} from-telemetry]
  (let [nb (crud/retrieve-by-id-as-admin parent)]
    (->> [:cpu :ram :disk :network :power-consumption]
         (map (fn [metric]
                (->> (nuvlabox-status->metric-data nuvlabox-status nb metric from-telemetry)
                     (map #(merge
                             {:nuvlaedge-id parent
                              :metric       (name metric)
                              :timestamp    current-time}
                             %)))))
         (apply concat))))

(defn nuvlabox-status->bulk-insert-metrics-request
  [nb-status from-telemetry]
  (let [body (->> (nuvlabox-status->bulk-insert-metrics-request-body nb-status from-telemetry)
                  ;; only retain metrics where a timestamp is defined
                  (filter :timestamp))]
    (when (seq body)
      {:headers     {"bulk" true}
       :params      {:resource-name ts-nuvlaedge-telemetry/resource-type
                     :action        "bulk-insert"}
       :body        body
       :nuvla/authn auth/internal-identity})))

(defn bulk-insert-metrics
  [nb-status from-telemetry]
  (try
    (some-> nb-status
            (nuvlabox-status->bulk-insert-metrics-request from-telemetry)
            (crud/bulk-action))
    (catch Exception ex
      (log/error "An error occurred inserting metrics: " ex))))

(defn ->predefined-aggregations-resp
  [{:keys [mode nuvlaedge-ids aggregations] group-by-field :group-by} resp]
  (let [ts-data    (fn [tsds-stats]
                     (map
                       (fn [{:keys [key_as_string doc_count] :as bucket}]
                         {:timestamp    key_as_string
                          :doc-count    doc_count
                          :aggregations (->> (keys aggregations)
                                             (select-keys bucket))})
                       (:buckets tsds-stats)))
        dimensions (case mode
                     :single-edge-query
                     {:nuvlaedge-id (first nuvlaedge-ids)}
                     :multi-edge-query
                     {:nuvlaedge-count (count nuvlaedge-ids)})
        hits       (second resp)]
    (if group-by-field
      (for [{:keys [key tsds-stats]} (get-in resp [0 :aggregations :by-field :buckets])]
        (cond->
          {:dimensions (assoc dimensions group-by-field key)
           :ts-data    (ts-data tsds-stats)}
          (seq hits) (assoc :hits hits)))
      [(cond->
         {:dimensions dimensions
          :ts-data    (ts-data (get-in resp [0 :aggregations :tsds-stats]))}
         (seq hits) (assoc :hits hits))])))

(defn ->custom-es-aggregations-resp
  [{:keys [mode nuvlaedge-ids]} resp]
  (let [ts-data    (fn [tsds-stats]
                     (map
                       (fn [{:keys [key_as_string doc_count] :as bucket}]
                         {:timestamp    key_as_string
                          :doc-count    doc_count
                          :aggregations (dissoc bucket :key_as_string :key :doc_count)})
                       (:buckets tsds-stats)))
        dimensions (case mode
                     :single-edge-query
                     {:nuvlaedge-id (first nuvlaedge-ids)}
                     :multi-edge-query
                     {:nuvlaedge-count (count nuvlaedge-ids)})]
    [(merge {:dimensions dimensions}
            (into {} (for [agg-key (keys (get-in resp [0 :aggregations]))]
                       [agg-key (ts-data (get-in resp [0 :aggregations agg-key]))])))]))

(defn ->raw-resp
  [{:keys [mode nuvlaedge-ids]} resp]
  (let [dimensions (case mode
                     :single-edge-query
                     {:nuvlaedge-id (first nuvlaedge-ids)}
                     :multi-edge-query
                     {:nuvlaedge-count (count nuvlaedge-ids)})
        hits       (second resp)]
    [{:dimensions dimensions
      :ts-data    (sort-by :timestamp hits)}]))

(defn ->metrics-resp
  [{:keys [predefined-aggregations custom-es-aggregations raw] :as options} resp]
  (cond
    predefined-aggregations
    (->predefined-aggregations-resp options resp)

    raw
    (->raw-resp options resp)

    custom-es-aggregations
    (->custom-es-aggregations-resp options resp)))

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

(defn build-ts-query [{:keys [last nuvlaedge-ids from to additional-filters orderby] :as options}]
  (let [nuvlabox-id-filter (str "nuvlaedge-id=[" (str/join " " (map #(str "'" % "'")
                                                                    nuvlaedge-ids))
                                "]")
        time-range-filter  (str "@timestamp>'" (time/to-str from) "'"
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
                                                (into [nuvlabox-id-filter
                                                       time-range-filter]
                                                      additional-filters)))
                              ")"))}
                      orderby (assoc :orderby orderby))}
      aggregation-clause
      (assoc :params {:tsds-aggregation (json/write-str aggregation-clause)}))))

(defn build-availability-query [options]
  ;; return up to 10000 availability state updates
  (build-ts-query (assoc options :last 10000)))

(defn query-availability
  [options]
  (->> (build-availability-query options)
       (crud/query-as-admin ts-nuvlaedge-availability/resource-type)
       (->metrics-resp options)))

(defn query-availability-raw
  ([options]
   (query-availability-raw options nil))
  ([{:keys [nuvlaedge-ids from to ts-interval] :as _options} search-after]
   (let [{{{total-hits :value} :total :keys [hits]} :hits :keys [aggregations]}
         (crud/query-native
           ts-nuvlaedge-availability/resource-type
           (cond->
             {:size         10000
              :query        {:constant_score
                             {:filter
                              {:bool
                               {:filter
                                (cond-> [{:terms {"nuvlaedge-id" nuvlaedge-ids}}]
                                        from (conj {:range {"@timestamp" {:gt (time/to-str from)}}})
                                        to (conj {:range {"@timestamp" {:lt (time/to-str to)}}}))}}
                              :boost 1.0}}
              :aggregations {:timestamps
                             {:date_histogram
                              {:field           "@timestamp"
                               :fixed_interval  ts-interval
                               :min_doc_count   0
                               :extended_bounds {:min (time/to-str from)
                                                 :max (time/to-str to)}}
                              :aggregations {}}}
              :sort         [{"@timestamp" "asc"}
                             {"nuvlaedge-id" "asc"}]}
             search-after (assoc :search_after search-after)))
         timestamps (->> aggregations
                         :timestamps
                         :buckets
                         (map (comp time/date-from-str :key_as_string)))]
     [total-hits hits timestamps])))

(defn build-telemetry-query [{:keys [raw metric] :as options}]
  (build-ts-query (-> options
                      (assoc :additional-filters [(str "metric='" metric "'")])
                      (cond-> raw (assoc :last max-data-points)))))

(defn query-metrics
  [options]
  (->> (build-telemetry-query options)
       (crud/query-as-admin ts-nuvlaedge-telemetry/resource-type)
       (->metrics-resp options)))

(defn latest-availability-status
  ([nuvlaedge-id]
   (latest-availability-status nuvlaedge-id nil))
  ([nuvlaedge-id before-timestamp]
   (->> {:cimi-params {:filter  (cimi-params-impl/cimi-filter
                                  {:filter (cond-> (str "nuvlaedge-id='" nuvlaedge-id "'")
                                                   before-timestamp
                                                   (str " and @timestamp<'" (time/to-str before-timestamp) "'"))})
                       :select  ["@timestamp" "online"]
                       :orderby [["@timestamp" :desc]]
                       :last    1}
         ;; sending an empty :tsds-aggregation to avoid acl checks. TODO: find a cleaner way
         :params      {:tsds-aggregation "{}"}}
        (crud/query-as-admin ts-nuvlaedge-availability/resource-type)
        second
        first)))

(defn nuvlabox-status->insert-availability-request-body
  [{:keys [parent online] :as _nuvlabox-status} from-telemetry]
  (when (some? online)
    (let [nb (crud/retrieve-by-id-as-admin parent)]
      ;; when online status is sent via heartbeats, do not store those sent via telemetry
      (when (or (not (utils/has-heartbeat-support? nb)) (not from-telemetry))
        (let [now    (time/now)
              latest (latest-availability-status (:id nb) now)]
          ;; when availability status has changed, or no availability data was recorded for the year yet
          (when (or (not= (:online latest)
                          (if online 1 0))
                    (not= (some-> (:timestamp latest) time/date-from-str time/year)
                          (time/year now)))
            {:nuvlaedge-id parent
             :timestamp    (time/to-str now)
             :online       (if (true? online) 1 0)}))))))

(defn nuvlabox-status->insert-availability-request
  [nb-status from-telemetry]
  (let [body (nuvlabox-status->insert-availability-request-body nb-status from-telemetry)]
    (when body
      {:params      {:resource-name ts-nuvlaedge-availability/resource-type}
       :body        body
       :nuvla/authn auth/internal-identity})))

(defn track-availability
  [nb-status from-telemetry]
  (try
    (some-> nb-status
            (nuvlabox-status->insert-availability-request from-telemetry)
            (crud/add))
    (catch Exception ex
      (log/error "An error occurred updating availability: " ex))))

(defn all-latest-availability-status
  ([nuvlaedge-ids]
   (all-latest-availability-status nuvlaedge-ids nil))
  ([nuvlaedge-ids before-timestamp]
   (->> {:size  10000
         :query {:constant_score
                 {:filter
                  {:bool {:filter (cond->
                                    [{:terms {:nuvlaedge-id nuvlaedge-ids}}]
                                    before-timestamp
                                    (conj {:range {"@timestamp" {:lt (time/to-str before-timestamp)}}}))}}}}
         :aggs  {:most_recent {:terms {:field "nuvlaedge-id"
                                       :size  10000}
                               :aggs  {:latest_hit {:top_hits {:sort [{"@timestamp" {:order "desc"}}]
                                                               :size 1}}}}}}
        (query-with-timeout ts-nuvlaedge-availability/resource-type latest-availability-query-timeout)
        (#(get-in % [:aggregations :most_recent :buckets]))
        (map (fn [bucket]
               (let [source (get-in bucket [:latest_hit :hits :hits 0 :_source])]
                 (-> source
                     (assoc :timestamp (time/date-from-str (get source (keyword "@timestamp"))))
                     (dissoc (keyword "@timestamp")))))))))

(defn all-latest-availability-transient-hashmap
  [nuvlaedge-ids before-timestamp]
  (reduce
    (fn [ret x]
      (let [k :nuvlaedge-id]
        (assoc! ret (get x k) (dissoc! (transient x) k))))
    (transient {})
    (all-latest-availability-status nuvlaedge-ids before-timestamp)))

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
   a metric datapoint before the given timestamp, or if it never sent a telemetry datapoint, nil otherwise"
  [{:keys [created first-availability] :as _nuvlabox} timestamp]
  (and (some-> created
               (time/before? timestamp))
       (or (nil? first-availability)                        ; do not exclude edges that never sent telemetry
           (some-> first-availability
                   :timestamp
                   (time/before? timestamp)))))

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
    (mapv #(update % :created time/date-from-str) nuvlaboxes)))

(defn granularity->duration
  "Converts from a string of the form <n>-<units> to java.time duration"
  [granularity]
  (let [[_ n unit] (re-matches #"(.*)-(.*)" (name granularity))]
    (try
      (time/duration (Integer/parseInt n) (keyword unit))
      (catch Exception _
        (logu/log-and-throw-400 (str "unrecognized value for granularity " granularity))))))

(defn precompute-query-params
  [{:keys [predefined-aggregations granularity] :as query-opts}]
  (cond-> query-opts
          predefined-aggregations (assoc :granularity-duration (granularity->duration granularity))))

(defn available-before?
  [{:keys [first-availability] :as _nuvlabox} timestamp]
  (or (nil? first-availability)                             ; do not exclude edges that never sent telemetry
      (-> first-availability :timestamp (time/before? timestamp))))

(defn filter-available-before-period-end
  [{:keys [to nuvlaboxes] :as params}]
  (let [nuvlaboxes (filterv #(available-before? % to) nuvlaboxes)]
    (assoc params
      :nuvlaboxes nuvlaboxes
      :nuvlaedge-ids (mapv :id nuvlaboxes))))

(defn all-first-availability-status
  [nuvlaedge-ids]
  (->> {:size    10000
        :_source ["@timestamp", "online"]
        :query   {:constant_score
                  {:filter
                   {:bool {:filter [{:terms {:nuvlaedge-id nuvlaedge-ids}}]}}}}
        :aggs    {:first_av {:terms {:field "nuvlaedge-id"
                                     :size  10000}
                             :aggs  {:first_hit {:top_hits {:sort [{"@timestamp" {:order "asc"}}]
                                                            :size 1}}}}}}
       (query-with-timeout ts-nuvlaedge-availability/resource-type first-availability-query-timeout)
       (#(get-in % [:aggregations :first_av :buckets]))
       (map (fn [bucket]
              (let [source (get-in bucket [:first_hit :hits :hits 0 :_source])]
                (-> source
                    (assoc :timestamp (time/date-from-str (get source (keyword "@timestamp"))))
                    (dissoc (keyword "@timestamp"))))))))

(defn assoc-first-availability
  [{:keys [nuvlaboxes nuvlaedge-ids] :as params}]
  (let [first-av   (doall (->> (all-first-availability-status nuvlaedge-ids)
                               (group-by :nuvlaedge-id)))
        nuvlaboxes (->> nuvlaboxes
                        (mapv (fn [{:keys [id] :as nb}]
                                (assoc nb :first-availability
                                          (some-> (first (get first-av id))
                                                  (select-keys [:online :timestamp]))))))]
    (assoc params :nuvlaboxes nuvlaboxes)))

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
        latest-av       (->> (all-latest-availability-status nuvlaedge-ids first-bucket-ts)
                             (group-by :nuvlaedge-id))
        nuvlaboxes      (->> nuvlaboxes
                             (map (fn [{:keys [id] :as nb}]
                                    (assoc nb :latest-availability
                                              (some-> (first (get latest-av id))
                                                      (select-keys [:online :timestamp]))))))]
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

(defn metrics-data->csv [options dimension-keys meta-keys metric-keys data-fn response]
  (with-open [writer (StringWriter.)]
    ;; write csv header
    (csv/write-csv writer [(concat (map name dimension-keys)
                                   (map name meta-keys)
                                   (map name metric-keys))])
    ;; write csv data
    (let [df (DecimalFormat. "0.####" (DecimalFormatSymbols. Locale/US))]
      (csv/write-csv writer
                     (for [{:keys [dimensions ts-data]} response
                           data-point ts-data]
                       (concat (map dimensions dimension-keys)
                               (map data-point meta-keys)
                               (map (fn [metric-key]
                                      (let [v (data-fn options data-point metric-key)]
                                        (if (float? v)
                                          ;; format floats with 4 decimal and dot separator
                                          (.format df v)
                                          v)))
                                    metric-keys)))))
    (.toString writer)))

(defn csv-export-fn
  [dimension-keys-fn meta-keys-fn metric-keys-fn data-fn]
  (fn [{:keys [resps] :as options}]
    (throw-custom-aggregations-not-exportable options)
    (metrics-data->csv
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
                              :query-fn        query-availability
                              :post-process-fn (comp timestamps->str
                                                     dissoc-hits
                                                     compute-nuvlabox-availability
                                                     assoc-latest-availability
                                                     timestamps->date)
                              :response-aggs   [:avg-online]
                              :csv-export-fn   (availability-csv-export-fn)}
   "cpu-stats"               {:metric         "cpu"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :query-fn       query-metrics
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
                              :query-fn       query-metrics
                              :aggregations   {:avg-ram-capacity {:avg {:field :ram.capacity}}
                                               :avg-ram-used     {:avg {:field :ram.used}}}
                              :csv-export-fn  (telemetry-csv-export-fn :ram)}
   "disk-stats"              {:metric         "disk"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :query-fn       query-metrics
                              :group-by       :disk.device
                              :aggregations   {:avg-disk-capacity {:avg {:field :disk.capacity}}
                                               :avg-disk-used     {:avg {:field :disk.used}}}
                              :csv-export-fn  (telemetry-csv-export-fn :disk)}
   "network-stats"           {:metric         "network"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :query-fn       query-metrics
                              :group-by       :network.interface
                              :aggregations   {:bytes-received    {:max {:field :network.bytes-received}}
                                               :bytes-transmitted {:max {:field :network.bytes-transmitted}}}
                              :csv-export-fn  (telemetry-csv-export-fn :network)}
   "power-consumption-stats" {:metric         "power-consumption"
                              :pre-process-fn assoc-nuvlaedge-ids
                              :query-fn       query-metrics
                              :group-by       :power-consumption.metric-name
                              :aggregations   {:energy-consumption {:max {:field :power-consumption.energy-consumption}}}
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
              (cond-> ts-data-point
                      nb-bucket
                      (update-in [:aggregations :by-edge :buckets]
                                 (fn [buckets] (conj (or buckets []) nb-bucket))))))))
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
                                              :edge-avg-online {:value (or availability 0.0)}}]))]
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

(defn compute-bucket
  "For the given period compute:
  - total commissioned time
  - total online time
  - n edges"
  [{:keys [nuvlaboxes] :as options} now start end latests hits idx total-hits skipped]
  (let [end (time/min-time now end)]
    (loop [latests                 latests
           hits                    hits
           hits-count              (.length hits)
           total-hits              total-hits
           skipped                 skipped
           idx                     idx
           total-commissioned-time 0.0
           total-online-time       0.0
           doc-count               0]
      (let [hit       (when (< idx hits-count) (some-> (nth hits idx) :_source))
            timestamp (some-> (get hit (keyword "@timestamp")) time/date-from-str)]
        (if (and (< idx hits-count) (time/before? timestamp end))
          (let [nuvlaedge-id   (:nuvlaedge-id hit)
                latest         (get latests nuvlaedge-id)
                prev-online    (get latest :online)
                prev-timestamp (time/max-time (get latest :timestamp) start)
                secs           (time/time-between prev-timestamp timestamp :seconds)]
            (recur (assoc! latests nuvlaedge-id
                           (if latest
                             (assoc! latest
                                     :timestamp timestamp
                                     :online (get hit :online))
                             (transient {:timestamp timestamp
                                         :online    (get hit :online)})))
                   hits
                   (.length hits)
                   total-hits
                   skipped
                   (inc idx)
                   (if latest (+ total-commissioned-time secs) total-commissioned-time)
                   (if (= prev-online 1) (+ total-online-time secs) total-online-time)
                   (inc doc-count)))
          (let [processed (+ skipped hits-count)]
            (if (and (pos? hits-count) (= idx hits-count))
              ;; fetch more data from ES
              (let [last-hit-sort-values (some-> (nth hits (dec idx)) :sort)
                    [total-hits hits] (query-availability-raw options last-hit-sort-values)]
                (recur latests
                       hits
                       (.length hits)
                       total-hits
                       processed
                       0
                       total-commissioned-time
                       total-online-time
                       doc-count))
              ;; compute remaining intervals to end of bucket
              (let [nb-length (.length nuvlaboxes)
                    [total-commissioned-time total-online-time bucket-edges-count]
                    (loop [total-commissioned-time total-commissioned-time
                           total-online-time       total-online-time
                           edge-idx                0
                           bucket-edges-count      0]
                      (if (= nb-length edge-idx)
                        [total-commissioned-time total-online-time bucket-edges-count]
                        (let [nb   (nth nuvlaboxes edge-idx)
                              secs (time/time-between
                                     (time/max-time
                                       (:created nb)
                                       (:timestamp (get latests (:id nb)))
                                       start)
                                     end
                                     :seconds)]
                          (recur
                            (+ total-commissioned-time secs)
                            (if (= (:online (get latests (:id nb))) 1)
                              (+ total-online-time secs)
                              total-online-time)
                            (inc edge-idx)
                            (if (or (some? (get latests (:id nb)))
                                    (and (nil? (:first-availability nb))
                                         (time/before? (:created nb) end))) ; count edges that never sent telemetry after creation
                              (inc bucket-edges-count)
                              bucket-edges-count)))))
                    bucket    {:start                   (time/to-str start)
                               :end                     (time/to-str end)
                               :total-commissioned-time total-commissioned-time
                               :total-online-time       total-online-time
                               :doc-count               doc-count
                               :n-edges                 bucket-edges-count}]
                [latests hits idx total-hits skipped bucket]))))))))

(defn used-memory []
  (let [runtime (Runtime/getRuntime)]
    (- (.totalMemory runtime) (.freeMemory runtime))))

(defn query-and-process-availabilities*
  [{:keys [nuvlaedge-ids from granularity-duration int-atom] :as options}]
  (let [now              (time/now)
        latests          (all-latest-availability-transient-hashmap nuvlaedge-ids from)
        [total-hits hits timestamps] (query-availability-raw options)
        initial-used-mem (used-memory)
        gc-limit         (* 100 1024 1024)                  ;; do a gc every 100mb of used mem
        ]
    (loop [timestamps timestamps
           latests    latests
           hits       hits
           idx        0
           total-hits total-hits
           skipped    0
           buckets    (transient [])
           used-mem   (used-memory)]
      (if (empty? timestamps)
        (persistent! buckets)
        (let [start (first timestamps)
              end   (time/plus start granularity-duration)
              [latests hits idx total-hits first bucket]
              (compute-bucket options now start end latests hits idx total-hits skipped)]
          (when @int-atom
            (throw (InterruptedException.)))
          (when (> (- used-mem initial-used-mem) gc-limit)
            (System/gc))
          (recur (rest timestamps)
                 latests
                 hits
                 idx
                 total-hits
                 first
                 (conj! buckets bucket)
                 (used-memory)))))))

(defn query-and-process-availabilities
  [{:keys [predefined-aggregations nuvlaboxes] :as options}]
  (if predefined-aggregations
    (let [ret (query-and-process-availabilities* options)]
      [{:dimensions {:nuvlaedge-count (count nuvlaboxes)}
        :ts-data    (mapv
                      (fn [{:keys [start n-edges doc-count total-commissioned-time total-online-time] :as _bucket}]
                        (let [virtual-edges-online (* n-edges (/ (double total-online-time)
                                                                 total-commissioned-time))]
                          {:timestamp    start
                           :doc-count    doc-count
                           :aggregations {:edges-count           {:value n-edges}
                                          :virtual-edges-online  {:value virtual-edges-online}
                                          :virtual-edges-offline {:value (- n-edges virtual-edges-online)}}}))
                      ret)}])
    (query-availability options)))

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
    {"availability-stats"      {:metric         "availability"
                                :pre-process-fn (comp filter-available-before-period-end
                                                      assoc-first-availability
                                                      precompute-query-params
                                                      update-nuvlaboxes-dates
                                                      throw-too-many-nuvlaboxes
                                                      (partial assoc-commissioned-nuvlaboxes ["id" "created"]))
                                :query-fn       query-and-process-availabilities
                                :response-aggs  [:edges-count
                                                 :virtual-edges-online
                                                 :virtual-edges-offline]
                                :csv-export-fn  (availability-csv-export-fn)}
     "availability-by-edge"    {:metric          "availability"
                                :pre-process-fn  (comp filter-available-before-period-end
                                                       assoc-first-availability
                                                       precompute-query-params
                                                       update-nuvlaboxes-dates
                                                       throw-too-many-nuvlaboxes
                                                       (partial assoc-commissioned-nuvlaboxes ["id" "name" "created"]))
                                :query-fn        query-availability
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
                                :query-fn       query-metrics
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
                                :query-fn       query-metrics
                                :aggregations   {:avg-ram-capacity     (group-by-edge {:by-edge {:avg {:field :ram.capacity}}})
                                                 :avg-ram-used         (group-by-edge {:by-edge {:avg {:field :ram.used}}})
                                                 :sum-avg-ram-capacity {:sum_bucket {:buckets_path :avg-ram-capacity>by-edge}}
                                                 :sum-avg-ram-used     {:sum_bucket {:buckets_path :avg-ram-used>by-edge}}}
                                :response-aggs  [:sum-avg-ram-capacity :sum-avg-ram-used]
                                :csv-export-fn  (telemetry-csv-export-fn :ram)}
     "disk-stats"              {:metric         "disk"
                                :pre-process-fn assoc-nuvlaedge-ids
                                :query-fn       query-metrics
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
                                :query-fn       query-metrics
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
                                :query-fn       query-metrics
                                :group-by       :power-consumption.metric-name
                                :aggregations   {:energy-consumption     (group-by-edge {:by-edge {:max {:field :power-consumption.energy-consumption}}})
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
    (let [max-n-buckets max-data-points
          n-buckets     (.dividedBy (time/duration from to)
                                    (granularity->duration granularity))]
      (when (> n-buckets max-n-buckets)
        (logu/log-and-throw-400 "too many data points requested. Please restrict the time interval or increase the time granularity."))))
  params)

(defn throw-response-format-not-supported
  [{:keys [accept-header] :as params}]
  (when (and (some? accept-header) (not (#{"application/json" "text/csv"} accept-header)))
    (logu/log-and-throw-400 (str "format not supported: " accept-header)))
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

(defn assoc-base-query-opts
  [{:keys [predefined-aggregations granularity filter] :as params} request]
  (assoc params
    :base-query-opts
    (-> (select-keys params [:id :from :to :granularity
                             :raw :custom-es-aggregations :predefined-aggregations
                             :mode :int-atom])
        (assoc :request request)
        (cond->
          filter
          (assoc :cimi-filter filter)
          predefined-aggregations
          (assoc :ts-interval (granularity->ts-interval granularity))))))

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
  (let [{:keys [pre-process-fn query-fn post-process-fn] :as dataset-opts} (get datasets-opts dataset-key)
        {:keys [predefined-aggregations] :as query-opts} (merge base-query-opts dataset-opts)
        query-opts (if pre-process-fn (doall (pre-process-fn query-opts)) query-opts)]
    (cond->> (doall (query-fn query-opts))
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
                  (exec-with-timeout!
                    query-data-executor
                    (fn [int-atom]
                      (try
                        (query-data (assoc params :int-atom int-atom) request)
                        (catch InterruptedException e
                          (log/error "Query execution was interrupted")
                          (throw e))
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

