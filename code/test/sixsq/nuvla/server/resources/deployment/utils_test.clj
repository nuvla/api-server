(ns sixsq.nuvla.server.resources.deployment.utils-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment.utils :as t])
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
  (testing "customer active and can pay"
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
      (is (= {} (t/throw-when-payment-required {} {})))))
  (testing "customer active and price set but can't pay"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly false)]
      (is (= {} (t/throw-when-payment-required {} {})))))
  (let [billable-deployment-follow     {:module {:price {:price-id              "price_id"
                                                         :follow-customer-trial true}}}
        billable-deployment-not-follow {:module {:price {:price-id              "price_id"
                                                         :follow-customer-trial false}}}]
    (testing "customer is active"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->subscription (constantly {:status "active"})]
        (is (= billable-deployment-follow
               (t/throw-when-payment-required billable-deployment-follow {})))))
    (testing "customer is past_due"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->subscription (constantly {:status "past_due"})]
        (is (= billable-deployment-follow
               (t/throw-when-payment-required billable-deployment-follow {})))))
    (testing "customer trialing with module price set and follow trial"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly false)]
        (is (= (t/throw-when-payment-required billable-deployment-follow {})
               billable-deployment-follow))))
    (testing "customer trialing with module price set and follow trial is not set"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly false)]
        (is (thrown-with-msg? ExceptionInfo
                              #"Valid subscription and payment method are needed!"
                              (t/throw-when-payment-required billable-deployment-not-follow {})))))

    (testing "customer trialing with module price set and follow trial is not set but can pay"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly true)]
        (is (= (t/throw-when-payment-required billable-deployment-not-follow {})
               billable-deployment-not-follow))))
    (testing "vendor or user with edit-data rights"
      (with-redefs [config-nuvla/*stripe-api-key* "123"
                    a/can-edit-data?              (constantly true)]
        (is (= (t/throw-when-payment-required billable-deployment-not-follow {})
               billable-deployment-not-follow))))))

(deftest trial-end
  (is (int? (t/trial-end "" {}))))

(deftest create-stripe-subscription
  (with-redefs [crud/retrieve-by-id-as-admin     identity
                pricing-impl/create-subscription identity]
    (is (map? (t/create-stripe-subscription nil {} nil)))))

(deftest cred-edited?
  (are [expected parent current-parent]
    (= expected (t/cred-edited? parent current-parent))
    false nil nil
    false nil "credential/x"
    false "credential/x" "credential/x"
    true "credential/y" "credential/x"))
