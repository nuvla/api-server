(ns sixsq.nuvla.server.resources.nuvlabox.utils-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as t]
    [sixsq.nuvla.server.util.time :as time]))

(deftest check-short-nb-id
  (is (nil? (t/short-nb-id nil)))
  (is (= "abc" (t/short-nb-id "nuvlabox/abc-def-ghi-jkl"))))

(deftest has-job-pull-support?
  (is (false? (t/has-job-pull-support? {:capabilities []})))
  (is (false? (t/has-job-pull-support? {})))
  (is (false? (t/has-job-pull-support? {:capabilities ["ANYTHING"]})))
  (is (t/has-job-pull-support? {:capabilities ["ANYTHING" t/capability-job-pull]})))

(deftest has-heartbeat-support?
  (is (false? (t/has-heartbeat-support? {:capabilities []})))
  (is (false? (t/has-heartbeat-support? {})))
  (is (false? (t/has-heartbeat-support? {:capabilities ["ANYTHING"]})))
  (is (t/has-heartbeat-support? {:capabilities ["ANYTHING" t/capability-heartbeat]})))

(deftest can-heartbeat?
  (is (false? (t/can-heartbeat? {})))
  (is (false? (t/can-heartbeat? {:state t/state-suspended})))
  (is (true? (t/can-heartbeat? {:state t/state-commissioned})))
  (is (true? (t/can-heartbeat? {:state        t/state-commissioned
                                 :capabilities ["A"]})))
  (is (true? (t/can-heartbeat? {:state        t/state-commissioned
                                :capabilities [t/capability-heartbeat]}))))

(deftest compute-next-heartbeat
  (with-redefs [time/now #(time/parse-date "2023-08-24T13:14:39.121Z")]
    (let [tolerance-fn #(-> % (* 2) (+ 10))]
     (is (nil? (t/compute-next-report nil tolerance-fn)))
     (is (= (t/compute-next-report 10 tolerance-fn) "2023-08-24T13:15:09.121Z"))
     (is (= (t/compute-next-report 20 tolerance-fn) "2023-08-24T13:15:29.121Z")))
    (is (= (t/compute-next-report 10 #(+ % 10)) "2023-08-24T13:14:59.121Z"))))

(deftest throw-when-payment-required
  (testing "stripe is disabled"
    (with-redefs [config-nuvla/*stripe-api-key* nil
                  a/is-admin?                   (constantly false)]
      (is (= (t/throw-when-payment-required {}) {}))))
  (testing "is admin"
    (with-redefs [config-nuvla/*stripe-api-key* "123"
                  a/is-admin?                   (constantly true)]
      (is (= (t/throw-when-payment-required {}) {}))))
  (testing "customer is active"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->subscription (constantly {:status "active"})]
      (is (= (t/throw-when-payment-required {}) {}))))
  (testing "customer is trialing"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->subscription (constantly {:status "trialing"})]
      (is (= (t/throw-when-payment-required {}) {}))))
  (testing "customer past_due"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->subscription (constantly {:status "past_due"})]
      (is (= (t/throw-when-payment-required {}) {})))))

(deftest throw-value-should-be-bigger
  (let [request          {}
        request-user-nok {:body {:some-key 1}}
        request-admin    (assoc request-user-nok
                           :nuvla/authn {:claims ["group/nuvla-admin"]})]
    (is (= request (t/throw-value-should-be-bigger request :some-key 10)))
    (is (thrown-with-msg?
          Exception
          #"some-key should not be less than 10!"
          (t/throw-value-should-be-bigger request-user-nok :some-key 10)))
    (is (= request-admin (t/throw-value-should-be-bigger request-admin :some-key 10)))))

(deftest legacy-heartbeat
  (let [nb-status {:parent "nuvlabox/1"}]
    (with-redefs [t/nuvlabox-request? (constantly true)]
      (is (true? (:online (t/legacy-heartbeat nb-status {} #(hash-map :id %))))))
    (with-redefs [t/nuvlabox-request? (constantly false)]
      (is (nil? (:online (t/legacy-heartbeat nb-status {} #(hash-map :id %))))))))
