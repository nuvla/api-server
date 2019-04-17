(ns sixsq.nuvla.server.resources.common.utils-test
  (:require
    [clj-time.core :as time]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.common.utils :as t]))

(deftest check-expired?-and-not-expired?
  (let [past-time (-> 10 time/minutes time/ago t/unparse-timestamp-datetime)
        future-time (-> 10 time/minutes time/from-now t/unparse-timestamp-datetime)]
    (is (false? (t/expired? nil)))
    (is (true? (t/expired? past-time)))
    (is (false? (t/expired? future-time)))
    (is (true? (t/not-expired? nil)))
    (is (false? (t/not-expired? past-time)))
    (is (true? (t/not-expired? future-time)))))

(deftest check-select-desc-keys
  (let [resource {:name        "name"
                  :description "description"
                  :tags        #{"one", "two"}}]
    (is (= resource (t/select-desc-keys resource)))
    (is (= resource (t/select-desc-keys (assoc resource :other "ignored"))))
    (is (= (dissoc resource :name) (t/select-desc-keys (dissoc resource :name))))))
