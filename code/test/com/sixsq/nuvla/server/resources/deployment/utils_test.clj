(ns com.sixsq.nuvla.server.resources.deployment.utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is testing]]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.pricing.payment :as payment]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.deployment.utils :as t]
    [com.sixsq.nuvla.server.util.time :as time])
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

(deftest cred-edited?
  (are [expected parent current-parent]
    (= expected (t/cred-edited? parent current-parent))
    false nil nil
    false nil "credential/x"
    false "credential/x" "credential/x"
    true "credential/y" "credential/x"))

(deftest keep-defined-values
  (let [m-a {:name "a" :value "a-v" :description "a-d"}
        m-b {:name "b" :description "b-d"}]
    (are [expected arg-map]
      (= expected (t/keep-current-defined-values (:arg1 arg-map) (:arg2 arg-map)))
      [] {:arg1 [] :arg2 []}
      [] {:arg1 [m-a] :arg2 []}
      [m-a] {:arg1 [m-a] :arg2 [{:name "a" :description "a-d"}]}
      [m-a m-b] {:arg1 [m-a m-b] :arg2 [{:name "a" :description "a-d"} m-b]}
      [(assoc m-a :description "a-d-changed") m-b] {:arg1 [m-a m-b] :arg2 [{:name "a" :description "a-d-changed"} m-b]}
      [m-a m-b] {:arg1 [m-a m-b] :arg2 [m-a (assoc m-b :value "any")]})))

(deftest throw-can-not-access-helm-repo-cred
  (is (= (t/throw-can-not-access-helm-repo-cred {}) {})))
