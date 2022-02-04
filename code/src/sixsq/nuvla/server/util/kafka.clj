(ns sixsq.nuvla.server.util.kafka
  "Kafka client can be configured using KAFKA_CLIENT_CONF_xxx env vars. For
  example to set 'delivery.timeout.ms' to 12345, provide it as the following
  environment variable: KAFKA_CLIENT_CONF_DELIVERY_TIMEOUT_MS=12345"
  (:require
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [kinsky.client :as kc]
    [sixsq.nuvla.utils.env :as eu]
    [clojure.string :as str]))


(def ^:dynamic *producer* nil)

(def ^:const warn-producer-no-initialised "Kafka producer is not initialized.")

(def ^:const kafka-client-conf-prefix ":kafka-client-conf-")

(def ^:const producer-name-default "nuvla-server")


(defn client-params-from-env
  "Given a map of env vars 'env-vars', return a map with Kafka only params
  filtered out by 'prefix', and keys transformed into Kafka client params.
  Example of the transformation:
  Environment var: KAFKA_CLIENT_CONF_DELIVERY_TIMEOUT_MS=12345 ->
  Clojure env var: {:kafka-client-conf-delivery-timeout-ms 12345} ->
  Kafka parameter: {:delivery.timeout.ms 12345}"
  [env-vars prefix]
  (->> env-vars
       (filter #(str/starts-with? (-> % first str) prefix))
       (map #(identity [(-> %
                            (first)
                            str
                            (clojure.string/replace (re-pattern prefix) "")
                            (clojure.string/replace #"-" ".")
                            keyword) (second %)]))
       (into {})))


(defn create-producer
  "Creates a client connecting to an instance of Kafka.
  Connection parameters (host:port) are taken from environment variables.
  Default serializers for key and value - string and JSON"
  ([]
   (let [bootstrap-servers (or (env/env :kafka-endpoints) "localhost:9092")]
     (create-producer bootstrap-servers)))
  ([bootstrap-servers & {:keys [kserializer vserializer producer-name]
                         :or   {kserializer   :string
                                vserializer   kc/json-serializer
                                producer-name producer-name-default}}]
   (let [config (merge {:bootstrap.servers (or bootstrap-servers
                                               (or (env/env :kafka-endpoints) "localhost:9092"))
                        :client.id         producer-name}
                       (client-params-from-env env/env kafka-client-conf-prefix))]
     (log/infof "creating kafka producer with config: %s" config)
     (kc/producer config
                  kserializer
                  vserializer))))


(defn publish
  [t k v]
  (if *producer*
    @(kc/send! *producer* t k v)
    (log/warn warn-producer-no-initialised)))


(defn publish-async
  [t k v]
  (if *producer*
    (kc/send! *producer* t k v)
    (log/warn warn-producer-no-initialised)))


(defn set-producer!
  [producer]
  (log/info "setting new kafka producer:" producer)
  (alter-var-root #'*producer* (constantly producer))
  (publish-async "nuvla-kafka" nil {:producer (str producer)}))


(defn close-producer! []
  (when *producer*
    (kc/close! *producer*)
    (set-producer! nil)))


(defn load-and-set-producer
  "Creates and sets kafka producer if KAFKA_PRODUCER_INIT environment variable
  is set to 'yes'."
  [& [bootstrap-servers]]
  (when (eu/env-get-as-boolean :kafka-producer-init)
    (set-producer! (create-producer bootstrap-servers))))
