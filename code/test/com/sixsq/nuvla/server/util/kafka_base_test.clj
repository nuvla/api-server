(ns com.sixsq.nuvla.server.util.kafka-base-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.util.kafka :as k]))


(defn cleanup
  [f]
  (k/comm-chan-set! k/comm-chan-len)
  (f)
  (k/close-producers!)
  (k/comm-chan-set! k/comm-chan-len))


(use-fixtures :each cleanup)


(deftest client-params-from-env-test
  (is (= {} (k/client-params-from-env {} ":foo-bar-")))
  (is (= {} (k/client-params-from-env {:env-var 1} ":foo-bar-")))
  (is (= {:param.one "one" :param.two "two"}
         (k/client-params-from-env {:foo-bar-param-one "one"
                                    :foo-bar-param-two "two"
                                    :bar-baz-param-one "baz"} ":foo-bar-"))))


(deftest comm-chan-init-defaults
  ; the comm channel is always there as it is defined on namespace load
  (is (some? @k/comm-chan!))
  (is (= k/comm-chan-len (-> @k/comm-chan! k/comm-chan-info :size)))
  (is (= 0 (-> @k/comm-chan! k/comm-chan-info :count))))


(deftest comm-chan-reset
  ; the comm channel is always there as it is defined on namespace load
  (is (some? @k/comm-chan!))
  (let [curr-len (-> @k/comm-chan! k/comm-chan-info :size)]
    (k/comm-chan-set! 5)
    (is (not (nil? @k/comm-chan!)))
    (is (= 5 (-> @k/comm-chan! k/comm-chan-info :size)))
    (is (= 0 (-> @k/comm-chan! k/comm-chan-info :count)))
    (k/comm-chan-set! curr-len)))


(deftest register-kafka-producer
  (is (= 0 (count @k/producers!)))
  (k/register-producer 0 nil)
  (is (= 1 (count @k/producers!))))


(deftest register-and-destroy-kafka-producer
  (k/close-producers!)
  (is (= 0 (count @k/producers!)))
  (k/register-producer 0 nil)
  (is (= 1 (count @k/producers!)))
  (k/close-producers!)
  (is (= 0 (count @k/producers!))))

(defn all-values-not-nil? [m]
  (every? (complement nil?) (vals m)))

(deftest producers-lifecycle
  (is (some? @k/comm-chan!))
  (k/create-producers!)
  (is (= k/producers-num (count @k/producers!)))
  (is (every? some? (vals @k/producers!)))
  (k/close-producers!))
