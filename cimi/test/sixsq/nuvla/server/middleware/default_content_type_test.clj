(ns sixsq.nuvla.server.middleware.default-content-type-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.middleware.default-content-type :as t]))


(def ^:const test-content-type "application/json")


(def handler (t/default-content-type identity test-content-type))


(defn updated-content-type
  [request]
  (-> request
      handler
      (get-in [:headers "content-type"])))


(deftest no-content-type-with-body
  (let [result (updated-content-type {:body "placeholder"})]
    (is (= test-content-type result))))


(deftest no-content-type-no-body
  (let [result (updated-content-type {})]
    (is (nil? result))))


(deftest existing-content-type
  (let [content-type "text/x-existing"
        result (updated-content-type {:headers {"content-type" content-type}})]
    (is (= content-type result))))
