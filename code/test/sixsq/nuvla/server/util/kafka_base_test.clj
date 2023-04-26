(ns sixsq.nuvla.server.util.kafka-base-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.server.util.kafka :as k]))


(defn cleanup
  [f]
  (f)
  (k/close-producers!))


(use-fixtures :each cleanup)


(deftest client-params-from-env-test
  (is (= {} (k/client-params-from-env {} ":foo-bar-")))
  (is (= {} (k/client-params-from-env {:env-var 1} ":foo-bar-")))
  (is (= {:param.one "one" :param.two "two"}
         (k/client-params-from-env {:foo-bar-param-one "one"
                                    :foo-bar-param-two "two"
                                    :bar-baz-param-one "baz"} ":foo-bar-"))))


(deftest comm-chan-init-destroy
  (k/comm-chan-init)
  (is (not (nil? k/*comm-chan*)))
  (k/comm-chan-destroy)
  (is (nil? k/*comm-chan*)))


(deftest comm-chan-init-defaults
  (k/comm-chan-init)
  (is (not (nil? k/*comm-chan*)))
  ;; running again doesn't recreate the channel
  (let [sc k/*comm-chan*]
    (k/comm-chan-init)
    (is (= sc k/*comm-chan*)))

  (is (= k/comm-chan-len (.n (.buf k/*comm-chan*))))
  (is (= 0 (.count (.buf k/*comm-chan*)))))


(deftest comm-chan-init-params
  (k/comm-chan-init 5)
  (is (not (nil? k/*comm-chan*)))

  (is (= 5 (.n (.buf k/*comm-chan*))))
  (is (= 0 (.count (.buf k/*comm-chan*)))))


(deftest register-kafka-producer
  (is (= 0 (count k/*producers*)))
  (k/register-kafka-producer 0 nil)
  (is (= 1 (count k/*producers*))))


(deftest register-and-destroy-kafka-producer
  (k/-close-producers!)
  (is (= 0 (count k/*producers*)))
  (k/register-kafka-producer 0 nil)
  (is (= 1 (count k/*producers*)))
  (k/-close-producers!)
  (is (= 0 (count k/*producers*))))

(defn all-values-not-nil? [m]
  (every? (complement nil?) (vals m)))

(deftest producers-lifecycle
  (k/create-producers!)
  (is (not (nil? k/*comm-chan*)))
  (is (= k/comm-chan-len (.n (.buf k/*comm-chan*))))
  (is (= 0 (.count (.buf k/*comm-chan*))))
  (is (= k/publishers-num (count k/*producers*)))
  (is (all-values-not-nil? k/*producers*))
  (k/close-producers!))
