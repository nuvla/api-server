(ns com.sixsq.nuvla.db.es.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.utils.env :as eu]
    [environ.core :as env]
    [qbits.spandex :as spandex]))

(def ^:const status-yellow "yellow")
(def ^:const status-green "green")

(def ^:private ok-health-statuses #{status-green status-yellow})

(def ^:const sniff-interval-mills 5000)
(def ^:const sniff-after-failure-delay-mills 1000)

(def close! spandex/close!)

(defn create-client
  [options]
  (spandex/client options))

(defn create-sniffer
  [client options]
  (spandex/sniffer client (or options {})))

(defn- throw-if-cluster-not-healthy
  [status]
  (when-not (ok-health-statuses status)
    (throw (ex-info "status is not accepted" {:status (str status)}))))

(defn cluster-health
  [client indexes]
  (-> client
      (spandex/request {:url          [:_cluster :health (str/join "," indexes)]
                        :query-string {:wait_for_status status-yellow
                                       :timeout         "15s"}
                        :method       :get})
      :body
      :status))

(defn wait-for-cluster
  "Waits for the cluster to reach a healthy state. Throws if the cluster does
   not reach a healthy state before the timeout. Returns the client on success."
  [client]
  (let [status (cluster-health client [])]
    (throw-if-cluster-not-healthy status)
    client))

(defn index-exists?
  [client index-name]
  (try
    (= (:status (spandex/request client {:url [index-name] :method :head})) 200)
    (catch Exception _
      false)))

(defn refresh-index
  [client index-name]
  (spandex/request client {:url [index-name :_refresh]}))

(defn cleanup-index
  [client index-name]
  (when (index-exists? client index-name)
    (log/debug (str "cleaning up index: " index-name))
    (refresh-index client index-name)
    (spandex/request client {:url          [index-name :_delete_by_query]
                             :query-string {:refresh true}
                             :method       :post
                             :body         {:query {:match_all {}}}})))

(def ^:const ES_PORT "9200")
(def ^:const ES_HOST (str "localhost:" ES_PORT))

(defn create-es-client
  "Creates a client connecting to an Elasticsearch instance. The 0-arity
  version takes the host and port from the environment variable ES_ENDPOINTS,
  which is the comma separated list of host1[:port][,host2[:port],...]. If
  ES_ENDPOINTS is not set, 'localhost:9200' is used. The 1-arity version takes
  host1[:port] as a vector. If the vector is empty, ['localhost:9200']
  is used."
  ([]
   (let [env-endpoints (env/env :es-endpoints)
         endpoints     (-> (or (when-not (str/blank? env-endpoints) env-endpoints) ES_HOST)
                           (clojure.string/split #","))
         es-endpoints  (->> endpoints
                            (map #(if-not (.contains % ":") (str % ":" ES_PORT) %))
                            distinct)]
     (create-es-client es-endpoints)))
  ([es-endpoints]
   (let [endpoints {:hosts (if (empty? es-endpoints) [ES_HOST] es-endpoints)}]
     (log/info "creating elasticsearch client:" es-endpoints)
     (create-client endpoints))))

(defn load-es-client
  []
  (-> (create-es-client)
      wait-for-cluster))

(defn create-es-sniffer
  "Creates a sniffer connected to an Elasticsearch cluster. The 1-arity
  version takes Elasticsearch `client` and creates the sniffer using
  environment variables SNIFF_INTERVAL and SNIFF_AFTER_FAILURE_DELAY, or
  uses defaults if the environment variables are not set. The 2-arity
  version takes Elasticsearch `client` and `options` map, which can either
  be empty (then defaults will be used) or contain sniffer initialisation
  options."
  ([client]
   (let [interval (eu/env-get-as-int :es-sniff-interval sniff-interval-mills)
         delay    (eu/env-get-as-int :es-sniff-after-failure-delay sniff-after-failure-delay-mills)]
     (create-es-sniffer client {:sniff-interval            interval
                                :sniff-after-failure-delay delay})))
  ([client options]
   (log/info "creating elasticsearch sniffer:" options)
   (create-sniffer client (or options {}))))

(defn load-es-sniffer
  "Conditionally creates and returns Elasticsearch sniffer. For the sniffer to
  be loaded, ES_SNIFFER_INIT environment variable must be set to 'yes'."
  [client]
  (when (eu/env-get-as-boolean :es-sniffer-init)
    (create-es-sniffer client)))
