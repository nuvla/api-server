(ns sixsq.nuvla.db.es.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [qbits.spandex :as spandex]
    [sixsq.nuvla.db.es.binding :as esrb]))


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
  (-> client
      (spandex/request {:url    [index-name]
                        :method :get})
      :status
      (= 200)))


(defn reset-index
  [client index-name]
  (when (index-exists? client index-name)
    (spandex/request client {:url    [index-name]
                             :method :delete})))


(defn create-es-client
  "Creates a client connecting to an Elasticsearch instance. The 0-arity
  version takes the host and port from the environmental variables ES_HOST and
  ES_PORT. The 2-arity version takes these values as explicit parameters. If
  the host or port is nil, then \"localhost\" or \"9200\" are used,
  respectively."
  ([]
   (create-es-client (env/env :es-host) (env/env :es-port)))
  ([es-host es-port]
   (let [es-host (or es-host "localhost")
         es-port (or es-port "9200")
         hosts {:hosts [(str es-host ":" es-port)]}]

     (log/info "creating elasticsearch client:" es-host es-port)
     (esrb/create-client hosts))))
