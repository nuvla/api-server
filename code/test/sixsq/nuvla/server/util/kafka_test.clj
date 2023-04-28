(ns sixsq.nuvla.server.util.kafka-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [kinsky.client :as kc]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.kafka :as k]
    [sixsq.nuvla.server.util.kafka-base-test :refer [cleanup]]))


(use-fixtures :each ltu/with-test-kafka-fixture cleanup)

(def boostrap-servers (format "%s:%s" ltu/kafka-host ltu/kafka-port))

(defn k-consumer
  []
  (kc/consumer {:bootstrap.servers boostrap-servers
                :group.id "consumer-group-id"
                "enable.auto.commit" "false"
                "auto.offset.reset" "earliest"
                "isolation.level" "read_committed"}
               :string kc/json-deserializer) )


#_(deftest publish-consume-single
  (let [t (str "event-" (System/currentTimeMillis))
        v {:foo "bar"}]
    (k/publish! t "event" v)
    (let [consumer (k-consumer)]
      (kc/subscribe! consumer t)
      (let [consumed (kc/poll! consumer 7000)]
        (is (> (:count consumed) 0))
        (is (= v (-> consumed
                     :by-topic
                     (get t)
                     last
                     :value))))
      (is (= 0 (:count (kc/poll! consumer 7000))))
      (kc/stop! consumer))))


#_(deftest publish-consume-many
  (let [t (str "event-" (System/currentTimeMillis))
        msg-num 10]
    (doseq [i (range msg-num)]
      (k/publish! t "event" {:foo i}))
    (let [consumer (k-consumer)]
      (kc/subscribe! consumer t)
      (let [consumed (kc/poll! consumer 7000)]
        (is (= msg-num (:count consumed)))
        (doseq [m (-> consumed
                      :by-topic
                      (get t))]
          (is (<= 0 (-> m :value :foo) (- msg-num 1)))))
      (is (= 0 (:count (kc/poll! consumer 7000))))
      (kc/stop! consumer))))
