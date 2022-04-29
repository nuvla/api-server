(ns sixsq.nuvla.server.resources.nuvlabox.utils-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as t]))


(deftest check-short-nb-id
  (is (nil? (t/short-nb-id nil)))
  (is (= "abc" (t/short-nb-id "nuvlabox/abc-def-ghi-jkl"))))


(deftest check-has-pull-support?
  (is (false? (t/has-pull-support? {:capabilities []})))
  (is (false? (t/has-pull-support? {})))
  (is (false? (t/has-pull-support? {:capabilities ["ANYTHING"]})))
  (is (t/has-pull-support? {:capabilities ["ANYTHING" "NUVLA_JOB_PULL"]})))

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
