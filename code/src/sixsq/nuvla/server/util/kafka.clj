(ns sixsq.nuvla.server.util.kafka
  (:require
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [kinsky.client :as kc]))


(def ^:dynamic *producer* nil)


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
                                producer-name "nuvla-server"}}]
   (log/info (format "creating kafka producer '%s': %s" producer-name bootstrap-servers))
   (kc/producer {:bootstrap.servers bootstrap-servers
                 :client.id         producer-name}
                kserializer
                vserializer)))


(defn publish
  [t k v]
  (if *producer*
    @(kc/send! *producer* t k v)
    (log/warn "Kafka producer is not initialized.")))


(defn publish-async
  [t k v]
  (if *producer*
    (kc/send! *producer* t k v)
    (log/warn "Kafka producer is not initialized.")))


(defn set-producer!
  [producer]
  (log/info "setting new kafka producer:" producer)
  (alter-var-root #'*producer* (constantly producer))
  (publish-async "nuvla-kafka" nil {:producer (str producer)}))


(defn close-producer! []
  (when *producer*
    (kc/close! *producer*)
    (set-producer! nil)))


