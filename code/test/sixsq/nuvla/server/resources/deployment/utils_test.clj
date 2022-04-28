(ns sixsq.nuvla.server.resources.deployment.utils-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.deployment.utils :as t]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.auth.acl-resource :as a])
  (:import (clojure.lang ExceptionInfo)))


(deftest throw-when-payment-required
  (testing "stripe is disabled"
    (with-redefs [config-nuvla/*stripe-api-key* nil
                  a/is-admin?                   (constantly false)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "is admin"
    (with-redefs [config-nuvla/*stripe-api-key* "123"
                  a/is-admin?                   (constantly true)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer active and has payment"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly true)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer is trialing"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly false)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer past_due and has not payment"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "past_due"})
                  payment/can-pay?                   (constantly false)]
      (is (thrown-with-msg? ExceptionInfo
                            #"Valid subscription and payment method are needed!"
                            (t/throw-when-payment-required {} {})))))
  (testing "customer trialing with module price set and follow trial"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly false)]
      (is (= (t/throw-when-payment-required {:module {:price {:price-id              "price_id"
                                                              :follow-customer-trial true}}} {})
             {:module {:price {:price-id              "price_id"
                               :follow-customer-trial true}}}))))
  (testing "customer trialing with module price set and follow trial is not set"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly false)]
      (is (thrown-with-msg? ExceptionInfo
                            #"Valid subscription and payment method are needed!"
                            (t/throw-when-payment-required {:module {:price {:price-id "price_id"}}} {})))))
  (testing "customer trialing with module price set and follow trial is not set but can pay"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly true)]
      (is (= (t/throw-when-payment-required {:module {:price {:price-id              "price_id"
                                                              :follow-customer-trial false}}} {})
             {:module {:price {:price-id              "price_id"
                               :follow-customer-trial false}}}))))

  )