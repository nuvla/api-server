(ns com.sixsq.nuvla.db.es.binding
  "Binding protocol implemented for an Elasticsearch database that makes use
   of the Elasticsearch REST API."
  (:require [clojure.tools.logging :as log]
            [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
            [com.sixsq.nuvla.db.binding :refer [Binding]]
            [com.sixsq.nuvla.db.es.acl :as acl]
            [com.sixsq.nuvla.db.es.aggregation :as aggregation]
            [com.sixsq.nuvla.db.es.common.es-mapping :as mapping]
            [com.sixsq.nuvla.db.es.common.utils :as escu]
            [com.sixsq.nuvla.db.es.filter :as filter]
            [com.sixsq.nuvla.db.es.order :as order]
            [com.sixsq.nuvla.db.es.pagination :as paging]
            [com.sixsq.nuvla.db.es.script-utils :refer [get-update-script]]
            [com.sixsq.nuvla.db.es.select :as select]
            [com.sixsq.nuvla.db.es.utils :as esu]
            [com.sixsq.nuvla.db.utils.common :as cu]
            [com.sixsq.nuvla.server.util.response :as r]
            [qbits.spandex :as spandex])
  (:import (java.io Closeable)))

(defn create-index
  [client index]
  (try
    (let [{:keys [status]} (spandex/request client {:url [index], :method :head})]
      (if (= 200 status)
        (log/debug index "index already exists")
        (log/error "unexpected status code when checking" index "index (" status ")")))
    (catch Exception e
      (let [{:keys [status body]} (ex-data e)]
        (try
          (if (= 404 status)
            (let [{{:keys [acknowledged shards_acknowledged]} :body}
                  (spandex/request client {:url  [index], :method :put
                                           :body {:settings {:number_of_shards   3
                                                             :number_of_replicas 2}}})]
              (if (and acknowledged shards_acknowledged)
                (log/info index "index created")
                (log/warn index "index may or may not have been created")))
            (log/error "unexpected status code when checking" index "index (" status "). " body))
          (catch Exception e
            (let [{:keys [status body] :as _response} (ex-data e)
                  error (:error body)]
              (log/error "unexpected status code when creating" index "index (" status "). " (or error e)))))))))

(defn set-index-mapping
  [client index mapping]
  (try
    (let [{:keys [body status]} (spandex/request client {:url    [index :_mapping]
                                                         :method :put
                                                         :body   mapping})]
      (if (= 200 status)
        (log/info index "mapping updated")
        (log/warn index "mapping could not be updated (" status "). " body)))
    (catch Exception e
      (let [{:keys [status body] :as _response} (ex-data e)
            error (:error body)]
        (log/warn index "mapping could not be updated (" status "). " (or error e))))))

(defn shards-successful?
  [response]
  (pos? (get-in response [:body :_shards :successful])))

(defn noop?
  [response]
  (= (get-in response [:body :result]) "noop"))

(defn errors?
  [response]
  (get-in response [:body :errors]))

(defn no-body-failures?
  [response]
  (empty? (get-in response [:body :failures])))

(defn add-data
  [client {:keys [id] :as data} {:keys [refresh]
                                 :or   {refresh true}
                                 :as   _options}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index       (escu/collection-id->index collection-id)
          updated-doc (-> data
                          (acl-utils/force-admin-role-right-all)
                          (acl-utils/normalize-acl-for-resource))
          response    (spandex/request client {:url          [index :_create uuid]
                                               :query-string {:refresh refresh}
                                               :method       :put
                                               :body         updated-doc})
          success?    (shards-successful? response)]
      (if success?
        (r/response-created id)
        (r/response-conflict id)))
    (catch Exception e
      (let [{:keys [status body] :as _response} (ex-data e)
            error (:error body)]
        (if (= 409 status)
          (r/response-conflict id)
          (r/response-error (str "unexpected exception: " (or error e))))))))

(defn update-data
  [client {:keys [id] :as data} {:keys [refresh]
                                 :or   {refresh true}
                                 :as   _options}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index       (escu/collection-id->index collection-id)
          updated-doc (-> data
                          (acl-utils/force-admin-role-right-all)
                          (acl-utils/normalize-acl-for-resource))
          response    (spandex/request client {:url          [index :_doc uuid]
                                               :query-string {:refresh refresh}
                                               :method       :put
                                               :body         updated-doc})
          success?    (shards-successful? response)]
      (if success?
        (r/json-response data)
        (r/response-conflict id)))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)]
        (r/response-error (str "unexpected exception updating " id ": " (or error e)))))))

(defn scripted-update-data
  [client id {:keys [body refresh retry_on_conflict]
              :or   {refresh           true
                     retry_on_conflict 3}
              :as   _options}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url          [index :_update uuid]
                                            :query-string {:refresh           refresh
                                                           :retry_on_conflict retry_on_conflict}
                                            :method       :post
                                            :body         body})
          success? (or (shards-successful? response)
                       (noop? response))]
      (if success?
        (r/map-response "updated successfully" 200 id)
        (r/response-conflict id)))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)]
        (r/response-error (str "unexpected exception scripted updating " id ": " (or error e)))))))

(defn find-data
  [client id]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url    [index :_doc uuid]
                                            :method :get})
          found?   (get-in response [:body :found])]
      (if found?
        (-> response :body :_source)
        (throw (r/ex-not-found id))))
    (catch Exception e
      (let [{:keys [status] :as _response} (ex-data e)]
        (if (= 404 status)
          (throw (r/ex-not-found id))
          (throw e))))))

(defn delete-data
  [client id]
  (let [[collection-id uuid] (cu/split-id id)
        index    (escu/collection-id->index collection-id)
        response (spandex/request client {:url          [index :_doc uuid]
                                          :query-string {:refresh true}
                                          :method       :delete})
        success? (shards-successful? response)
        deleted? (= "deleted" (get-in response [:body :result]))]
    (if (and success? deleted?)
      (r/response-deleted id)
      (r/response-error (str "could not delete document " id)))))

(defn timeout
  [{:keys [timeout]}]
  (when timeout
    {:timeout (str timeout "ms")}))

(defn query-data
  [client collection-id {:keys [cimi-params params] :as options}]
  (try
    (let [index                   (escu/collection-id->index collection-id)
          paging                  (paging/paging cimi-params)
          orderby                 (order/sorters cimi-params)
          aggregation             (merge-with merge
                                              (aggregation/aggregators cimi-params)
                                              (aggregation/custom-aggregations params))
          ts-aggregation          (aggregation/tsds-aggregators params)
          selected                (select/select cimi-params)
          query                   {:query (if ts-aggregation
                                            (filter/filter cimi-params)
                                            (acl/and-acl-query (filter/filter cimi-params) options))}
          timeout                 (timeout params)
          body                    (merge paging orderby selected query timeout aggregation ts-aggregation)
          response                (spandex/request client {:url    [index :_search]
                                                           :method :post
                                                           :body   body})
          count-before-pagination (-> response :body :hits :total :value)
          aggregations            (-> response :body :aggregations)
          meta                    (cond-> {:count count-before-pagination}
                                          aggregations (assoc :aggregations aggregations))
          hits                    (cond->> (->> response :body :hits :hits (map :_source))
                                           ts-aggregation
                                           (map (fn [hit] (-> hit
                                                              (assoc :timestamp (get hit (keyword "@timestamp")))
                                                              (dissoc (keyword "@timestamp"))))))]
      (if (shards-successful? response)
        [meta hits]
        (let [msg (str "error when querying: " (:body response))]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception querying: " (or error e))]
        (throw (r/ex-response msg 500))))))

(defn query-data-native
  [client collection-id query]
  (try
    (let [index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url    [index :_search]
                                            :method :post
                                            :body   query})]
      (if (shards-successful? response)
        (:body response)
        (let [msg (str "error when querying: " (:body response))]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception querying: " (or error e))]
        (throw (r/ex-response msg 500))))))

(defn add-metric-data
  [client collection-id data {:keys [refresh]
                              :or   {refresh true}
                              :as   _options}]
  (try
    (let [index        (escu/collection-id->index collection-id)
          updated-data (-> data
                           (dissoc :timestamp)
                           (assoc "@timestamp" (:timestamp data)))
          response     (spandex/request client {:url          [index :_doc]
                                                :query-string {:refresh refresh}
                                                :method       :post
                                                :body         updated-data})
          success?     (shards-successful? response)]
      (if success?
        {:status 201
         :body   {:status  201
                  :message (str collection-id " metric added")}}
        (r/response-conflict collection-id)))
    (catch Exception e
      (let [{:keys [status body] :as _response} (ex-data e)
            error (:error body)]
        (if (= 409 status)
          (r/response-conflict collection-id)
          (r/response-error (str "unexpected exception: " (or error e))))))))

(defn bulk-operation
  [client collection-id data _options]
  (try
    (let [index          (escu/collection-id->index collection-id)
          body           (spandex/chunks->body data)
          response       (spandex/request client {:url     [index :_bulk]
                                                  :method  :put
                                                  :headers {"Content-Type" "application/x-ndjson"}
                                                  :body    body})
          body-response  (:body response)
          success?       (not (errors? response))]
      (if success?
        body-response
        (let [items (:items body-response)
              msg   (str (if (seq items)
                           {:errors-count (count items)
                            :first-error  (first items)}
                           body-response))]
          (throw (r/ex-response msg 400)))))
    (catch Exception e
      (let [{:keys [body status]} (ex-data e)]
        (throw (r/ex-response (str body) (or status 500)))))))

(defn bulk-insert-metrics
  [client collection-id data options]
  (let [data-transform (fn [{:keys [timestamp] :as doc}]
                         (-> doc
                             (dissoc :timestamp)
                             (assoc "@timestamp" timestamp)))
        data (interleave (repeat {:create {}})
                         (map data-transform data))]
    (bulk-operation client collection-id data options)))

(defn bulk-edit-data
  [client collection-id
   {{:keys [doc]} :body
    :keys         [cimi-params operation] :as options}]
  (try
    (let [index         (escu/collection-id->index collection-id)
          body          {:query  (acl/and-acl-edit (filter/filter cimi-params) options)
                         :script (get-update-script doc operation)}
          ;; refresh index to minimize risk of version conflicts
          _             (esu/refresh-index client index)
          response      (spandex/request client {:url          [index :_update_by_query]
                                                 :method       :post
                                                 :query-string {:refresh true}
                                                 :body         body})
          body-response (:body response)
          success?      (no-body-failures? response)]
      (if success?
        body-response
        (let [msg (str "error when updating by query: " body-response)]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception updating by query: " _response (or error e))]
        (throw (r/ex-response msg 500))))))

(defn bulk-delete-data
  [client collection-id {:keys [cimi-params] :as options}]
  (try
    (let [index         (escu/collection-id->index collection-id)
          query         {:query (acl/and-acl-delete (filter/filter cimi-params) options)}
          response      (spandex/request client {:url          [index :_delete_by_query]
                                                 :query-string {:refresh true}
                                                 :method       :post
                                                 :body         query})
          body-response (:body response)
          success?      (-> body-response :failures empty?)]
      (if success?
        body-response
        (let [msg (str "error when deleting by query: " body-response)]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception delete by query: " (or error e))]
        (throw (r/ex-response msg 500))))))

(def hot-warm-cold-delete-policy
  {:hot    {:min_age "0ms"
            :actions {:set_priority {:priority 100}
                      :rollover     {:max_primary_shard_size "10gb"
                                     :max_age                "1d"}}}
   :warm   {:min_age "7d"
            :actions {:set_priority {:priority 50}
                      :downsample   {:fixed_interval "1h"}}}
   :cold   {:min_age "30d"
            :actions {:set_priority {:priority 0}
                      :downsample   {:fixed_interval "1d"}}}
   :delete {:min_age "365d"
            :actions {:delete {:delete_searchable_snapshot true}}}})

(def hot-delete-policy
  {:hot    {:min_age "0ms"
            :actions {:set_priority {:priority 100}
                      :rollover     {:max_age "30d"}}}
   :delete {:min_age "375d"                                 ;; 1 year + 10 days margin
            :actions {:delete {:delete_searchable_snapshot true}}}})

(defn create-or-update-lifecycle-policy
  [client index ilm-policy]
  (let [policy-name (str index "-ilm-policy")]
    (try
      (let [{:keys [status]}
            (spandex/request
              client
              {:url    [:_ilm :policy policy-name]
               :method :put
               :body   {:policy
                        {:_meta  {:description (str "ILM policy for " index)}
                         :phases ilm-policy}}})]
        (if (= 200 status)
          (do (log/debug policy-name "ILM policy created/updated")
              policy-name)
          (log/error "unexpected status code when creating/updating" policy-name "ILM policy (" status ")")))
      (catch Exception e
        (let [{:keys [status body] :as _response} (ex-data e)
              error (:error body)]
          (log/error "unexpected status code when creating/updating" policy-name "ILM policy (" status "). " (or error e)))))))

(defn create-timeseries-template
  [client index mapping {:keys [routing-path look-back-time look-ahead-time start-time lifecycle-name]}]
  (let [template-name (str index "-template")]
    (try
      (let [{:keys [status]} (spandex/request client
                                              {:url    [:_index_template template-name],
                                               :method :put
                                               :body   {:index_patterns [(str index "*")],
                                                        :data_stream    {},
                                                        :template
                                                        {:settings
                                                         (cond->
                                                           {:index.mode       "time_series",
                                                            :number_of_shards 3
                                                            ;:index.look_back_time         "7d",
                                                            ;:index.look_ahead_time        "2h",
                                                            ;:index.time_series.start_time "2023-01-01T00:00:00.000Z"
                                                            ;:index.lifecycle.name  "nuvlabox-status-ts-1d-hf-ilm-policy"
                                                            }
                                                           routing-path (assoc :index.routing_path routing-path)
                                                           look-ahead-time (assoc :index.look_ahead_time look-ahead-time)
                                                           look-back-time (assoc :index.look_back_time look-back-time)
                                                           start-time (assoc :index.time_series.start_time start-time)
                                                           lifecycle-name (assoc :index.lifecycle.name lifecycle-name))
                                                         :mappings mapping}}})]
        (if (= 200 status)
          (do (log/debug template-name "index template created/updated")
              template-name)
          (log/error "unexpected status code when creating/updating" template-name "index template (" status ")")))
      (catch Exception e
        (let [{:keys [status body] :as _response} (ex-data e)
              error (:error body)]
          (log/error "unexpected status code when creating/updating" template-name "index template (" status "). " (or error e)))))))

(defn create-datastream
  [client datastream-index-name]
  (try
    (let [{:keys [status]} (spandex/request client {:url [:_data_stream datastream-index-name], :method :get})]
      (if (= 200 status)
        (log/debug datastream-index-name "datastream already exists")
        (log/error "unexpected status code when checking" datastream-index-name "datastream (" status ")")))
    (catch Exception e
      (let [{:keys [status body]} (ex-data e)]
        (try
          (if (= 404 status)
            (let [{{:keys [acknowledged]} :body}
                  (spandex/request client {:url [:_data_stream datastream-index-name], :method :put})]
              (if acknowledged
                (log/info datastream-index-name "datastream created")
                (log/warn datastream-index-name "datastream may or may not have been created")))
            (log/error "unexpected status code when checking" datastream-index-name "datastream (" status "). " body))
          (catch Exception e
            (let [{:keys [status body] :as _response} (ex-data e)
                  error (:error body)]
              (log/error "unexpected status code when creating" datastream-index-name "datastream (" status "). " (or error e)))))))))

(defn initialize-timeserie-datastream
  [client collection-id {:keys [spec ilm-policy look-back-time look-ahead-time start-time]
                         :or   {ilm-policy     hot-warm-cold-delete-policy
                                look-back-time "7d"}
                         :as   _options}]
  (let [index           (escu/collection-id->index collection-id)
        mapping         (mapping/mapping spec {:dynamic-templates false, :fulltext false})
        routing-path    (mapping/time-series-routing-path spec)
        ilm-policy-name (create-or-update-lifecycle-policy client index ilm-policy)]
    (create-timeseries-template client index mapping {:routing-path    routing-path
                                                      :lifecycle-name  ilm-policy-name
                                                      :look-ahead-time look-ahead-time
                                                      :look-back-time  look-back-time
                                                      :start-time      start-time})
    (create-datastream client index)))

(defn initialize-db
  [client collection-id {:keys [spec timeseries] :as options}]
  (let [index (escu/collection-id->index collection-id)]
    (if timeseries
      (initialize-timeserie-datastream client collection-id options)
      (let [mapping (mapping/mapping spec)]
        (create-index client index)
        (set-index-mapping client index mapping)))))

(deftype ElasticsearchRestBinding [client sniffer]
  Binding

  (initialize [_ collection-id options]
    (initialize-db client collection-id options))


  (add [_ data options]
    (add-data client data options))


  (retrieve [_ id _options]
    (find-data client id))


  (delete [_ {:keys [id]} _options]
    (delete-data client id))


  (edit [_ data options]
    (update-data client data options))

  (scripted-edit [_ id options]
    (scripted-update-data client id options))

  (query [_ collection-id options]
    (query-data client collection-id options))

  (query-native [_ collection-id query]
    (query-data-native client collection-id query))

  (add-metric [_ collection-id data options]
    (add-metric-data client collection-id data options))

  (bulk-insert-metrics [_ collection-id data options]
    (bulk-insert-metrics client collection-id data options))

  (bulk-operation [_ collection-id data options]
    (bulk-operation client collection-id data options))

  (bulk-delete [_ collection-id options]
    (bulk-delete-data client collection-id options))

  (bulk-edit [_ collection-id options]
    (bulk-edit-data client collection-id options))

  Closeable
  (close [_]
    (when sniffer
      (spandex/close! sniffer))
    (spandex/close! client)))
