(ns sixsq.nuvla.server.util.kafka
  "Kafka client can be configured using KAFKA_CLIENT_CONF_xxx env vars. For
  example to set 'delivery.timeout.ms' to 12345, provide it as the following
  environment variable: KAFKA_CLIENT_CONF_DELIVERY_TIMEOUT_MS=12345

  A simple async model for publishing messages from API server resources to Kafka.

  API Server Resources -> Comm Channel -> Kafka Producers -> Kafka topics

  API server resources use async publishing via (publish topic key value). Under
  the hood a sliding buffer of a fixed length is used as the communication
  channel between the resources and a set of the internal Kafka producers. Both
  the length of the buffer and number of Kafka producers is configurable via
  env vars on the server startup. Kafka producers publishing the the key/value
  messages to the topic provided by the resources.
  "
  (:require
    [clojure.core.async :as a :refer [chan put! <!]]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [kinsky.client :as kc]
    [sixsq.nuvla.utils.env :as eu]))


; Number of publishers to Kafka.
(def ^:const publishers-num 5)

; Length of the communication channel between clients and Kafka publishers.
(def ^:const comm-chan-len 250)

; Communication channel.
(def ^:dynamic *comm-chan* nil)

; {id producer} map of Kafka producers (used for bookkeeping).
(def ^:dynamic *producers* {})

; Prefix of expected env vars for configuration of the Kafka client.
(def ^:const kafka-client-conf-prefix ":kafka-client-conf-")

; Prefix name of the producers.
(def ^:const producer-name-prefix "nuvla-server")

; Default Kafka endpoint.
(def ^:const kafka-endpoints "localhost:9092")


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


(defn kafka-bootstrap-servers-from-env
  "Return Kafka bootstrap servers from environment or default value."
  []
  (or (env/env :kafka-endpoints) kafka-endpoints))


(defn kafka-comm-channel-len-from-env
  "Return the length of the communication channel with internal Kafka producers
  from environment or default value."
  []
  (eu/env-get-as-int :kafka-comm-channel-len comm-chan-len))


(defn kafka-publishers-num-from-env
  "Return number of channel consumers from environment or default value."
  []
  (eu/env-get-as-int :kafka-publishers-num publishers-num))


(defn kafka-producer
  "Creates a client connecting to an instance of Kafka.
  If not provided, the connection parameters (host:port) are taken from environment.
  Default serializers for key and value - string and JSON"
  ([id]
   (kafka-producer id (kafka-bootstrap-servers-from-env)))
  ([id bootstrap-servers & {:keys [kserializer vserializer]
                            :or   {kserializer :string
                                   vserializer kc/json-serializer}}]
   (let [config (merge {:bootstrap.servers (or bootstrap-servers
                                               (kafka-bootstrap-servers-from-env))
                        :client.id         (str producer-name-prefix "-" id)}
                       (client-params-from-env env/env kafka-client-conf-prefix))]
     (log/infof "creating kafka producer with config: %s" config)
     (kc/producer config
                  kserializer
                  vserializer))))


(defn -delete-producers!
  []
  (alter-var-root #'*producers* (constantly {})))


(defn -close-producers!
  []
  (when (> (count *producers*) 0)
    (log/info "closing kafka producers:" *producers*)
    (map kc/close! (vals *producers*))
    (-delete-producers!)))


(defn chan-consumer-kafka-producer
  [id kafka-producer]
  (a/go
    (loop []
      (when-let [{:keys [topic key value] :as msg} (<! *comm-chan*)]
        (log/debugf "channel consumer %s consumed: %s" id msg)
        (kc/send! kafka-producer topic key value)
        (log/debugf "published: %s %s %s" topic key value)
        (recur)))))


(defn register-kafka-producer
  [n producer]
  (alter-var-root #'*producers* assoc n producer))


(defn start-channel-consumers
  ([]
   (start-channel-consumers publishers-num (kafka-bootstrap-servers-from-env)))
  ([num-consumers]
   (start-channel-consumers num-consumers (kafka-bootstrap-servers-from-env)))
  ([num-consumers bootstrap-servers]
   (doseq [i (range num-consumers)]
     (let [producer (kafka-producer i bootstrap-servers)]
       (chan-consumer-kafka-producer i producer)
       (register-kafka-producer i producer)))))


(defn async-chan-info
  [channel]
  {:size  (.n (.buf channel))
   :count (.count (.buf channel))})


(defn comm-chan-init
  [& [chan-len]]
  (if (nil? *comm-chan*)
    (let [len (or chan-len (kafka-comm-channel-len-from-env))]
      (log/info "initialise async channel of len:" len)
      (alter-var-root #'*comm-chan* (constantly (chan (a/sliding-buffer len)))))
    (log/warn "async channel already initialised:" (async-chan-info *comm-chan*))))


(defn comm-chan-destroy
  []
  (log/debugf "destroying comm chan: %s" *comm-chan*)
  (alter-var-root #'*comm-chan* (constantly nil)))


(defn -create-producers!
  [bootstrap-servers chan-len num-consumers]
  (comm-chan-init (or chan-len (kafka-comm-channel-len-from-env)))
  (start-channel-consumers
    (or num-consumers (kafka-publishers-num-from-env))
    (or bootstrap-servers (kafka-bootstrap-servers-from-env))))

;
; Public functions for using during the server startup and shutdown.
;

(defn create-producers!
  "Creates and sets kafka producers if KAFKA_PRODUCER_INIT environment variable
  is set to 'yes'."
  [& [bootstrap-servers chan-len num-consumers]]
  (if (eu/env-get-as-boolean :kafka-producer-init)
    (-create-producers! bootstrap-servers chan-len num-consumers)
    (log/warn "kafka producers will not be created.")))


(defn close-producers!
  "Closes all Kafka producers and destroys their communication channel with the
  client code."
  []
  (-close-producers!)
  (comm-chan-destroy))


;
; Public function for using from the server resources for publishing messages to
; Kafka topics.
;

(defn publish!
  "Publishes `key`/`value` message to Kafka `topic`."
  [topic key value]
  (log/debugf "publish: %s %s %s" topic key value)
  (put! *comm-chan* {:topic topic
                     :key   key
                     :value value}))
