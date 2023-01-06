(ns sixsq.nuvla.server.util.kafka-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [kinsky.client :as kc]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.kafka :as k]))


(def bootstrap-servers (format "%s:%s" ltu/kafka-host ltu/kafka-port))


(use-fixtures :once ltu/with-test-kafka-fixture)


(deftest client-params-from-env-test
  (is (= {} (k/client-params-from-env {} ":foo-bar-")))
  (is (= {} (k/client-params-from-env {:env-var 1} ":foo-bar-")))
  (is (= {:param.one "one" :param.two "two"}
         (k/client-params-from-env {:foo-bar-param-one "one"
                                    :foo-bar-param-two "two"
                                    :bar-baz-param-one "baz"} ":foo-bar-"))))


(deftest producer-lifecycle
  (k/close-producer!)
  (is (nil? k/*producer*))
  (k/set-producer! (k/create-producer bootstrap-servers))
  (is (not (nil? k/*producer*)))
  (k/close-producer!)
  (is (nil? k/*producer*)))


(deftest publish-consume
  (k/close-producer!)
  (is (nil? k/*producer*))
  (k/set-producer! (k/create-producer bootstrap-servers :vserializer kc/json-serializer))
  (is (not (nil? k/*producer*)))
  (let [t (str "events-" (System/currentTimeMillis))
        v {:foo "bar"}]
    (k/publish t "event" v)
    (let [consumer (kc/consumer {:bootstrap.servers bootstrap-servers
                                 :group.id "consumer-group-id"
                                 "enable.auto.commit" "false"
                                 "auto.offset.reset" "earliest"
                                 "isolation.level" "read_committed"}
                                :string kc/json-deserializer)]
      (kc/subscribe! consumer t)
      (let [consumed (kc/poll! consumer 7000)]
        (is (> (:count consumed) 0))
        (is (= v (-> consumed
                     :by-topic
                     (get t)
                     last
                     :value))))
      (is (= 0 (:count (kc/poll! consumer 7000))))
      (kc/stop! consumer)))
  (k/close-producer!))
