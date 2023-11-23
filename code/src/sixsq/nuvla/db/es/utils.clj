(ns sixsq.nuvla.db.es.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [qbits.spandex :as spandex]
    [sixsq.nuvla.db.es.binding :as esrb]
    [sixsq.nuvla.utils.env :as eu]))


(def ^:private ok-health-statuses #{"green" "yellow"})


(defn- throw-if-cluster-not-healthy
  [status]
  (when-not (ok-health-statuses status)
    (throw (ex-info "status is not accepted" {:status (str status)}))))


(defn cluster-health
  [client indexes]
  (-> client
      (spandex/request {:url          [:_cluster :health (str/join "," indexes)]
                        :query-string {:wait_for_status "yellow"
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
    (-> client
        (spandex/request {:url    [index-name]
                          :method :head})
        :status
        (= 200))
    (catch Exception _
      false)))


(defn cleanup-index
  [client index-name]
  (when (index-exists? client index-name)
    (log/debug (str "cleaning up index: " index-name))
    (spandex/request client {:url [:_refresh], :method :post})
    (spandex/request client {:url          [index-name :_delete_by_query]
                             :query-string {:refresh true}
                             :method       :post
                             :body         {:query {:match_all {}}}})))


(defn cleanup-indices
  [client indices-to-cleanup]
  (doseq [index indices-to-cleanup]
    (cleanup-index client index)))


(defn list-indices
  [client]
  (spandex/request client {:url    "/_cat/indices"
                           :method :get}))

(defn index-content
  [client index-name]
  (-> client
      (spandex/request {:url    [index-name]
                        :method :get})
      :body))


(defn get-document
  [client doc-path]
  (-> client
      (spandex/request {:url    doc-path
                        :method :get})
      :body))


(defn reset-index
  [client index-name]
  (when (index-exists? client index-name)
    (spandex/request client {:url    [index-name]
                             :method :delete})))


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
     (esrb/create-client endpoints))))


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
   (let [interval (eu/env-get-as-int :es-sniff-interval esrb/sniff-interval-mills)
         delay    (eu/env-get-as-int :es-sniff-after-failure-delay esrb/sniff-after-failure-delay-mills)]
     (create-es-sniffer client {:sniff-interval            interval
                                :sniff-after-failure-delay delay})))
  ([client options]
   (log/info "creating elasticsearch sniffer:" options)
   (esrb/create-sniffer client (or options {}))))


(defn load-es-sniffer
  "Conditionally creates and returns Elasticsearch sniffer. For the sniffer to
  be loaded, ES_SNIFFER_INIT environment variable must be set to 'yes'."
  [client]
  (when (eu/env-get-as-boolean :es-sniffer-init)
    (create-es-sniffer client)))
