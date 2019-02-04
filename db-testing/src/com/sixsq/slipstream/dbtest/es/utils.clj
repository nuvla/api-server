(ns com.sixsq.slipstream.dbtest.es.utils
  (:refer-clojure :exclude [read update])
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.db.es.acl :as acl]
    [com.sixsq.slipstream.db.es.aggregation :as agg]
    [com.sixsq.slipstream.db.es.filter :as ef]
    [com.sixsq.slipstream.db.es.order :as order]
    [com.sixsq.slipstream.db.es.pagination :as pg]
    [com.sixsq.slipstream.db.es.select :as select]
    [com.sixsq.slipstream.db.utils.common :as cu]
    [environ.core :as env]
    [me.raynes.fs :as fs])
  (:import
    (java.net InetAddress)
    (java.util UUID)
    (org.elasticsearch.action.admin.indices.create CreateIndexResponse)
    (org.elasticsearch.action.admin.indices.delete DeleteIndexRequest)
    (org.elasticsearch.action.admin.indices.exists.indices IndicesExistsRequest)
    (org.elasticsearch.action.admin.indices.get GetIndexRequest)
    (org.elasticsearch.action.bulk BulkRequestBuilder BulkResponse)
    (org.elasticsearch.action.search SearchPhaseExecutionException SearchRequestBuilder SearchResponse SearchType)
    (org.elasticsearch.action.support WriteRequest$RefreshPolicy)
    (org.elasticsearch.client Client)
    (org.elasticsearch.cluster.health ClusterHealthStatus)
    (org.elasticsearch.common.logging LogConfigurator)
    (org.elasticsearch.common.settings Settings)
    (org.elasticsearch.common.transport TransportAddress)
    (org.elasticsearch.common.unit TimeValue)
    (org.elasticsearch.common.xcontent XContentType)
    (org.elasticsearch.index IndexNotFoundException)
    (org.elasticsearch.index.query QueryBuilders)
    (org.elasticsearch.node MockNode Node)
    (org.elasticsearch.transport Netty4Plugin)
    (org.elasticsearch.transport.client PreBuiltTransportClient)))

(def ^:const max-result-window 200000)

(defn json->edn [json]
  (when json (json/read-str json :key-fn keyword)))

(defn edn->json [edn]
  (json/write-str edn))

(def ^:const doc-type "_doc")

;;
;; Elasticsearch implementations of CRUD actions
;;

(defn create
  [^Client client index type docid json]
  (.. client
      (prepareIndex index doc-type docid)
      (setCreate true)
      (setRefreshPolicy WriteRequest$RefreshPolicy/IMMEDIATE)
      (setSource json XContentType/JSON)
      (get)
      (status)))

(defn read
  [^Client client index type docid {:keys [cimi-params] :as options}]
  (let [get-request-builder (.. client
                                (prepareGet index doc-type docid))]
    (-> get-request-builder
        (select/add-selected-keys cimi-params)
        (.get))))

(defn update
  [^Client client index type docid json]
  (.. client
      (prepareUpdate index doc-type docid)
      (setRefreshPolicy WriteRequest$RefreshPolicy/IMMEDIATE)
      (setDoc json XContentType/JSON)
      (get)))

(defn delete
  [^Client client index type docid]
  (.. client
      (prepareDelete index doc-type docid)
      (get)))

(defn add-query [^SearchRequestBuilder request-builder options]
  (.setQuery request-builder (-> options
                                 ef/es-filter
                                 (acl/and-acl options))))

(defn search
  "On success, returns the full response in edn format. On errors, it logs the
   error and returns an empty map."
  ^SearchResponse [^Client client index type {:keys [cimi-params] :as options}]
  (try
    (let [^SearchRequestBuilder request (-> (.. client
                                                (prepareSearch (into-array String [index]))
                                                (setTypes (into-array String [(cu/de-camelcase doc-type)]))
                                                (setSearchType SearchType/DEFAULT))
                                            (add-query options)
                                            (select/add-selected-keys cimi-params)
                                            (pg/add-paging cimi-params)
                                            (order/add-sorters cimi-params)
                                            (agg/add-aggregators cimi-params))
          ^SearchResponse search-response (.get request)

          ;; FIXME: This status should be checked to ensure the query was successful.
          status (.getStatus (.status search-response))]

      (-> search-response str json->edn))
    (catch IndexNotFoundException infe
      (log/warn "index" index "not found, returning empty search result")
      {})
    (catch SearchPhaseExecutionException spee
      (log/warn "search failed on" index "for" type "with parameters" cimi-params
                ", returning empty search result; message:" (.getMessage spee))
      {})))

;;
;; Convenience functions
;;

(defn- add-index
  [client index type ^BulkRequestBuilder bulk-request-builder [uuid json]]
  (let [new-index (.. client
                      (prepareIndex index doc-type uuid)
                      (setCreate true)
                      (setSource json XContentType/JSON))]
    (.add bulk-request-builder new-index)))

(defn bulk-create
  ^BulkResponse [^Client client index type uuid-jsons]
  (let [bulk-request-builder (.. client
                                 (prepareBulk)
                                 (setRefreshPolicy WriteRequest$RefreshPolicy/WAIT_UNTIL))]
    (.. (reduce (partial add-index client index doc-type) bulk-request-builder uuid-jsons)
        (get))))

(defn dump
  [^Client client index type]
  (-> (.. client
          (prepareSearch (into-array String [index]))
          (setTypes (into-array String [doc-type]))
          (setSearchType SearchType/DEFAULT)
          (setQuery (QueryBuilders/matchAllQuery))
          (setSize 1)
          (get))
      str
      json->edn
      :hits
      :hits))

;;
;; Util functions
;;

(defn create-test-node
  "Creates a local elasticsearch node that holds data that can be access
   through the native or HTTP protocols."
  ([]
   (create-test-node (str (UUID/randomUUID))))
  ([^String cluster-name]
   (let [tempDir (str (fs/temp-dir "es-data-"))
         settings (.. (Settings/builder)
                      (put "cluster.name" cluster-name)
                      (put "path.home" tempDir)
                      (put "transport.netty.worker_count" 3)
                      (put "node.data" true)
                      (put "http.enabled" true)
                      (put "logger.level" "ERROR")
                      (put "http.type" "netty4")
                      (put "http.port" "9200-9300")
                      (put "transport.type" "netty4")
                      (put "network.host" "127.0.0.1")
                      (build))
         plugins [Netty4Plugin]
         ]

     (LogConfigurator/configureWithoutConfig settings)
     (.. (MockNode. ^Settings settings plugins)
         (start)))))



(def ^:const mapping-not-analyzed
  (-> "com/sixsq/slipstream/dbtest/es/mapping-not-analyzed.json"
      io/resource
      slurp))

(defn index-exists?
  [^Client client index-name]
  (.. client
      (admin)
      (indices)
      (exists (IndicesExistsRequest. (into-array String [index-name])))
      (get)
      (isExists)))

(defn create-index
  ^CreateIndexResponse [^Client client index-name]
  (log/info "creating index:" index-name)
  (let [settings (.. (Settings/builder)
                     (put "index.max_result_window" max-result-window)
                     (put "index.number_of_shards" 3)
                     (put "index.number_of_replicas" 0)
                     (build))]
    (.. client
        (admin)
        (indices)
        (prepareCreate index-name)
        (setSettings settings)
        (addMapping "_default_" mapping-not-analyzed XContentType/JSON)
        (get))))

(def ^:private ok-health-statuses #{ClusterHealthStatus/GREEN ClusterHealthStatus/YELLOW})

(defn- throw-if-cluster-not-healthy
  [status]
  (when-not (ok-health-statuses status)
    (throw (ex-info "status is not accepted" {:status (str status)}))))

(defn cluster-health
  [^Client client indexes]
  (.. client
      (admin)
      (cluster)
      (prepareHealth (into-array String indexes))
      (setWaitForGreenStatus)
      (setTimeout (TimeValue/timeValueSeconds 15))
      (get)))

(defn node-client [^Node node]
  (when node (.client node)))

(defn create-es-client
  "Creates a client connecting to an Elasticsearch instance. The 0-arity
  version takes the host and port from the environmental variables ES_HOST and
  ES_PORT. The 2-arity version takes these values as explicit parameters. If
  the host or port is nil, then \"localhost\" or \"9300\" are used,
  respectively."
  ([]
   (create-es-client (env/env :es-host) (env/env :es-port)))
  ([es-host es-port]
   (let [es-host (or es-host "localhost")
         es-port (or es-port "9300")]

     (log/info "creating elasticsearch client:" es-host es-port)
     (.. (new PreBuiltTransportClient ^Settings Settings/EMPTY [])
         (addTransportAddress (TransportAddress. (InetAddress/getByName es-host)
                                                 (read-string es-port)))))))

(defn wait-for-cluster
  [^Client client]
  (let [status (.. (cluster-health client [])
                   (getStatus))]
    (throw-if-cluster-not-healthy status)))

(defn wait-for-index
  [^Client client index]
  (let [status (.. (cluster-health client [index])
                   (getIndices)
                   (get index)
                   (getStatus))]
    (throw-if-cluster-not-healthy status)))

(defn reset-index
  [^Client client index-name]
  (when (index-exists? client index-name)
    (.. client
        (admin)
        (indices)
        (delete (DeleteIndexRequest. index-name))
        (get)))
  (create-index client index-name))

(defn get-all-indices
  "Returns array of index names as strings.  Useful with `refresh-all-indices`.
  Based on
  https://stackoverflow.com/questions/33134906/elasticsearch-find-all-indexes-using-the-java-client
  "
  [^Client client]
  (.. client
      (admin)
      (indices)
      (getIndex (GetIndexRequest.))
      (actionGet)
      (getIndices)))

(defn refresh-all-indices
  [^Client client]
  (.. client
      (admin)
      (indices)
      (prepareRefresh (get-all-indices client))
      (get)))

(defn refresh-indices
  [^Client client indexes]
  (.. client
      (admin)
      (indices)
      (prepareRefresh (into-array String indexes))
      (get)))

(defn refresh-index
  [^Client client index]
  (.. client
      (admin)
      (indices)
      (prepareRefresh (into-array String [index]))
      (get)))

(defn random-index-name
  "Creates a random value for an index name. The name is the string value of a
   random UUID, although that is an implementation detail."
  []
  (str (UUID/randomUUID)))

(defmacro with-es-test-client
  "Creates a new elasticsearch node, client, and test index, executes the body
   with `node`, `client`, and `index` vars bound to the values, and then
   reliably closes the node and client."
  [& body]
  `(with-open [~'node (create-test-node)
               ~'client (node-client ~'node)]
     (let [~'index (random-index-name)]
       (create-index ~'client ~'index)
       ~@body)))
