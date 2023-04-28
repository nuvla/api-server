(ns sixsq.nuvla.server.util.kafka
  "This namespace implements a simple async model for publishing messages from
  API server resources to Kafka.

  API Server Resources -> Comm Channel -> Kafka Producers -> Kafka topics

  API server resources use async publishing via (publish topic key value). Under
  the hood a sliding buffer of a fixed length is used as the communication
  channel between the resources with a set of the internal Kafka producers. Both
  the length of the buffer and number of Kafka producers is configurable via
  env vars on the server startup. Kafka producers publish the key/value messages
  to the topic provided by the resources.

  The use of the sliding buffer (implemented as non blocking buffer) is
  justified by the fact that Kafka producers can block but we don't want server
  resources to block on publication to the communication channel.

  The communication channel is a dynamic var to allow for external reset if
  required.

  Kafka client can be configured using KAFKA_CLIENT_CONF_xxx env vars. For
  example to set 'delivery.timeout.ms' to 12345, provide it as the following
  environment variable: KAFKA_CLIENT_CONF_DELIVERY_TIMEOUT_MS=12345"
  (:require
    [clojure.core.async :as a :refer [<! chan put!]]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [kinsky.client :as kc]
    [sixsq.nuvla.utils.env :as eu]))


; Number of producers to Kafka.
(def ^:const producers-num 5)

; {id producer} map of Kafka producers (used for bookkeeping).
(def producers! (atom {}))

; Prefix of expected env vars for configuration of the Kafka client.
(def ^:const kafka-client-conf-prefix ":kafka-client-conf-")

; Prefix name of the producers.
(def ^:const producer-name-prefix "nuvla-server")

; Default Kafka endpoint.
(def ^:const kafka-endpoints "localhost:9092")

; Length of the communication channel between clients and Kafka producers.
(def ^:const comm-chan-len 250)


(defn need-init?
  "If we need to initialise the components of the namespace."
  []
  (eu/env-get-as-boolean :kafka-producer-init))

;
; Async communication channel.
;

(defn comm-channel-len-from-env
  "Return the length of the communication channel with internal Kafka producers
  from environment or default value."
  []
  (eu/env-get-as-int :kafka-comm-channel-len comm-chan-len))


; Communication channel.
(def comm-chan! (atom (when (need-init?)
                        (let [len (comm-channel-len-from-env)]
                          (log/info "initialise async comm channel of len:" len)
                          (chan (a/sliding-buffer len))))))


(defn comm-chan-set!
  [len]
  (reset! comm-chan! (chan (a/sliding-buffer len))))


(defn comm-chan-info
  [^clojure.core.async.impl.channels.ManyToManyChannel channel]
  (let [chan ^clojure.core.async.impl.buffers.SlidingBuffer (.buf channel)]
    {:size  (.n chan)
     :count (.count chan)}))


;
; Kafka producers.
;

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
       (map (fn [[k v]]
              [(-> (str k)
                   (str/replace (re-pattern prefix) "")
                   (str/replace #"-" ".")
                   keyword) v]))
       (into {})))


(defn bootstrap-servers-from-env
  "Return Kafka bootstrap servers from environment or default value."
  []
  (or (env/env :kafka-endpoints) kafka-endpoints))


(defn producers-num-from-env
  "Return number of channel consumers from environment or default value."
  []
  (eu/env-get-as-int :kafka-producers-num producers-num))


(defn kafka-producer
  "Creates a client connecting to an instance of Kafka.
  If not provided, the connection parameters (host:port) are taken from environment.
  Default serializers for key and value - string and JSON"
  ([id]
   (kafka-producer id (bootstrap-servers-from-env)))
  ([id bootstrap-servers & {:keys [kserializer vserializer]
                            :or   {kserializer :string
                                   vserializer kc/json-serializer}}]
   (let [config (merge {:bootstrap.servers (or bootstrap-servers
                                               (bootstrap-servers-from-env))
                        :client.id         (str producer-name-prefix "-" id)}
                       (client-params-from-env env/env kafka-client-conf-prefix))]
     (log/infof "creating kafka producer with config: %s" config)
     (kc/producer config
                  kserializer
                  vserializer))))


(defn start-producer!
  [id producer]
  (a/go-loop []
    (if (some? @comm-chan!)
      (when-let [{:keys [topic key value] :as msg} (<! @comm-chan!)]
        (log/debugf "producer %s consumed from comm chan: %s" id msg)
        (try
          (kc/send! producer topic key value)
          (log/debugf "producer %s published: %s %s %s" id topic key value)
          (catch Exception e
            (log/errorf "producer %s %s failed publishing to kafka: %s" id producer e)))
        (recur))
      (log/warn "comm channel is not defined"))))


(defn register-producer
  [n producer]
  (swap! producers! assoc n producer))


(defn start-producers!
  ([num-producers bootstrap-servers]
   (doseq [i (range num-producers)]
     (let [producer (kafka-producer i bootstrap-servers)]
       (start-producer! i producer)
       (register-producer i producer)))))

;
; Public functions for using during the server startup and shutdown.
;

(defn create-producers!
  "Creates and sets kafka producers if KAFKA_PRODUCER_INIT environment variable
  is set to 'yes'."
  [& [bootstrap-servers]]
  (if (need-init?)
    (start-producers! (producers-num-from-env)
                      (or bootstrap-servers (bootstrap-servers-from-env)))
    (log/warn "kafka producers will not be created.")))


(defn close-producers!
  "Closes all Kafka producers and destroys their communication channel with the
  client code."
  []
  (when (seq @producers!)
    (log/debugf "closing kafka producers: %s" @producers!)
    (doseq [[id p] @producers!]
      (log/debugf "closing producer %s: %s" id p)
      (try
        (kc/close! p)
        (catch Exception e
          (log/errorf "failed closing producer %s %s: %s" id p e))))
    (reset! producers! {})))


;
; Public function for using from the server resources for publishing messages to
; Kafka topics.
;

(defn publish!
  "Publishes `key`/`value` message to Kafka `topic`."
  [topic key value]
  (log/debugf "publish: %s %s %s" topic key value)
  (put! @comm-chan! {:topic topic
                     :key   key
                     :value value}))
