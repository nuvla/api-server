(ns sixsq.nuvla.server.util.kafka
  (:require
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [kinsky.client :as kc]))


(def ^:dynamic *producer* nil)


(defn set-producer!
  [producer]
  (alter-var-root #'*producer* (constantly producer)))


(defn create-producer
  "Creates a client connecting to an instance of Kafka.
  Connection parameters (host:port) are taken from environment variables."
  ([]
   (let [bootstrap-servers (or (env/env :kafka-endpoints) "localhost:9092")]
     (create-producer bootstrap-servers)))
  ([bootstrap-servers]
   (log/info "creating kafka producer:" bootstrap-servers)
   (kc/producer {:bootstrap.servers bootstrap-servers}
                :string
                :string)))


(defn close-producer! []
  (when *producer*
    (kc/close! *producer*)
    (set-producer! nil)))


(defn publish
  [t k v]
  @(kc/send! *producer* t k v))

