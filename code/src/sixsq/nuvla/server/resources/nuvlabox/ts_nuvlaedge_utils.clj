(ns sixsq.nuvla.server.resources.nuvlabox.ts-nuvlaedge-utils
  (:require [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.ts-nuvlaedge :as ts-nuvlaedge]
            [sixsq.nuvla.server.util.time :as time])
  (:import (java.io StringWriter)))

(defn build-aggregations-clause
  [{:keys [granularity aggregations] group-by-field :group-by}]
  (let [tsds-aggregations {:tsds-stats
                           {:date_histogram
                            {:field          "@timestamp"
                             :fixed_interval granularity}
                            :aggregations aggregations}}]
    (if group-by-field
      {:aggregations
       {:by-field
        {:terms        {:field group-by-field}
         :aggregations tsds-aggregations}}}
      {:aggregations tsds-aggregations})))

(defn build-metrics-query [{:keys [nuvlaedge-ids from to metric] :as options}]
  (let [nuvlabox-id-filter (str "nuvlaedge-id=[" (str/join " " (map #(str "'" % "'")
                                                                    nuvlaedge-ids))
                                "]")
        time-range-filter  (str "@timestamp>'" (time/to-str from) "'"
                                " and "
                                "@timestamp<'" (time/to-str to) "'")
        metric-filter      (str "metric='" metric "'")]
    {:cimi-params {:last 0
                   :filter
                   (parser/parse-cimi-filter
                     (str "("
                          (apply str
                                 (interpose " and "
                                            [nuvlabox-id-filter
                                             time-range-filter
                                             metric-filter]))
                          ")"))}
     :params      {:tsds-aggregation (json/write-str (build-aggregations-clause options))}}))

(defn ->metrics-resp
  [{:keys [mode nuvlaedge-ids aggregations response-aggs] group-by-field :group-by} resp]
  (let [ts-data    (fn [tsds-stats]
                     (map
                       (fn [{:keys [key_as_string doc_count] :as bucket}]
                         {:timestamp    key_as_string
                          :doc-count    doc_count
                          :aggregations (->> (or response-aggs (keys aggregations))
                                             (select-keys bucket)
                                             #_(map (fn [[k agg-bucket]] [k (agg-resp agg-bucket)]))
                                             #_(into {}))})
                       (:buckets tsds-stats)))
        dimensions (case mode
                     :single-edge-query
                     {:nuvlaedge-id (first nuvlaedge-ids)}
                     :multi-edge-query
                     {:nuvlaedge-count (count nuvlaedge-ids)})]
    (if group-by-field
      (for [{:keys [key tsds-stats]} (get-in resp [0 :aggregations :by-field :buckets])]
        {:dimensions (assoc dimensions group-by-field key)
         :ts-data    (ts-data tsds-stats)})
      [{:dimensions dimensions
        :ts-data    (ts-data (get-in resp [0 :aggregations :tsds-stats]))}])))

(defn query-metrics
  [options]
  (->> (build-metrics-query options)
       (crud/query-as-admin ts-nuvlaedge/resource-type)
       (->metrics-resp options)))

(defn metrics-data->csv [dimension-keys aggregation-keys response]
  (with-open [writer (StringWriter.)]
    ;; write cav header
    (csv/write-csv writer [(concat (map name dimension-keys)
                                   ["timestamp" "doc-count"]
                                   (map name aggregation-keys))])
    ;; write csv data
    (csv/write-csv writer
                   (for [{:keys [dimensions ts-data]} response
                         {:keys [timestamp doc-count aggregations]} ts-data]
                     (concat (map dimensions dimension-keys)
                             [timestamp doc-count]
                             (map #(get-in aggregations [% :value]) aggregation-keys))))
    (.toString writer)))

(defmulti nuvlabox-status->metric-data (fn [_ metric] metric))

(defmethod nuvlabox-status->metric-data :default
  [{:keys [resources]} metric]
  (when-let [metric-data (get resources metric)]
    [{metric metric-data}]))

(defmethod nuvlabox-status->metric-data :online-status
  [{:keys [online]} _]
  (when (some? online)
    [{:timestamp     (time/now-str)
      :online-status {:online (if online 1 0)}}]))

(defmethod nuvlabox-status->metric-data :cpu
  [{{:keys [cpu]} :resources} _]
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
  [{{:keys [ram]} :resources} _]
  (when ram
    [{:ram (select-keys ram [:capacity :used])}]))

(defmethod nuvlabox-status->metric-data :disk
  [{{:keys [disks]} :resources} _]
  (when (seq disks)
    (mapv (fn [data] {:disk (select-keys data [:device :capacity :used])}) disks)))

(defmethod nuvlabox-status->metric-data :network
  [{{:keys [net-stats]} :resources} _]
  (when (seq net-stats)
    (mapv (fn [data] {:network (select-keys data [:interface :bytes-transmitted :bytes-received])}) net-stats)))

(defmethod nuvlabox-status->metric-data :power-consumption
  [{{:keys [power-consumption]} :resources} _]
  (when (seq power-consumption)
    (mapv (fn [data] {:power-consumption (select-keys data [:metric-name :energy-consumption :unit])}) power-consumption)))

(defn nuvlabox-status->ts-bulk-insert-request-body
  [{:keys [parent current-time] :as nuvlabox-status}]
  (->> [:online-status :cpu :ram :disk :network :power-consumption]
       (map (fn [metric]
              (->> (nuvlabox-status->metric-data nuvlabox-status metric)
                   (map #(merge
                           {:nuvlaedge-id parent
                            :metric       (name metric)
                            :timestamp    current-time}
                           %)))))
       (apply concat)))

(defn nuvlabox-status->ts-bulk-insert-request
  [nb-status]
  (let [body (->> (nuvlabox-status->ts-bulk-insert-request-body nb-status)
                  ;; only retain metrics where a timestamp is defined
                  (filter :timestamp))]
    (when (seq body)
      {:headers     {"bulk" true}
       :params      {:resource-name ts-nuvlaedge/resource-type
                     :action        "bulk-insert"}
       :body        body
       :nuvla/authn auth/internal-identity})))

(defn bulk-insert-metrics
  [nb-status]
  (some-> nb-status
          (nuvlabox-status->ts-bulk-insert-request)
          (crud/bulk-action)))

