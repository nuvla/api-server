(ns sixsq.nuvla.server.util.kafka-test
  (:require
    [clojure.test  :refer :all :as t]
    [kinsky.client :as kc]
    [sixsq.nuvla.server.util.kafka-embeded :as ke]
    [sixsq.nuvla.server.util.kafka :as k]
    [sixsq.nuvla.server.resources.common.utils :as cu]))

(def host "localhost")
(def kafka-port 9093)
(def zk-port 2183)
#_(def bootstrap-servers (format "%s:%s" host kafka-port))
(def bootstrap-servers "192.168.64.2:31497")

(t/use-fixtures
  :once (fn [f]
          (let [z-dir (ke/create-tmp-dir "zookeeper-data-dir")
                k-dir (ke/create-tmp-dir "kafka-log-dir")]
            (try
              (with-open [k (ke/start-embedded-kafka
                              {::ke/host host
                               ::ke/kafka-port kafka-port
                               ::ke/zk-port zk-port
                               ::ke/zookeeper-data-dir (str z-dir)
                               ::ke/kafka-log-dir (str k-dir)
                               ::ke/broker-config {"auto.create.topics.enable" "true"}})]
                (f))
              (catch Throwable t
                (throw t))
              (finally
                (ke/delete-dir z-dir)
                (ke/delete-dir k-dir))))))


(deftest producer-lifecycle
  (k/close-producer!)
  (is (nil? k/*producer*))
  (k/set-producer! (k/create-producer bootstrap-servers))
  (is (not (nil? k/*producer*)))
  (k/close-producer!)
  (is (nil? k/*producer*)))


(deftest publish-influxdb
  (k/set-producer! (k/create-producer bootstrap-servers))
  (is (not (nil? k/*producer*)))
  (let [t "events"
        uuid (cu/random-uuid)
        v (str (format "event,resource=deployment,user-id=%s,resource-id=deployment/%s" uuid uuid)
            ",category=action,state=%s state_int=10 %s")
        actions ["created" "started" "stopped" "deleted"]]
    (doseq [a actions]
      (let [msg (format v a (str (System/currentTimeMillis) "000000"))]
        (println "publishing: " msg)
        (k/publish t "event" msg))
      (Thread/sleep 1000)))
  (k/close-producer!))

(deftest publish-consume
  (k/set-producer! (k/create-producer bootstrap-servers))
  (is (not (nil? k/*producer*)))
  (let [t "events"
        v "foo:bar"]
    (k/publish t "event" v)
    (let [consumer (kc/consumer {:bootstrap.servers bootstrap-servers
                                 :group.id "consumer-group-id"
                                 "enable.auto.commit" "false"
                                 "auto.offset.reset" "earliest"
                                 "isolation.level" "read_committed"}
                                :string :string)]
      (kc/subscribe! consumer t)
      (let [consumed (kc/poll! consumer 5000)]
        (is (= 1 (:count consumed)))
        (is (= v (-> consumed
                     :by-topic
                     (get t)
                     first
                     :value))))
      (is (= 0 (:count (kc/poll! consumer 5000))))
      (kc/stop! consumer)))
  (k/close-producer!))
