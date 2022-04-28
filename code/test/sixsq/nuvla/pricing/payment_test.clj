(ns sixsq.nuvla.pricing.payment-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.pricing.payment :as t]
    [sixsq.nuvla.server.resources.common.crud :as crud]))

(deftest has-defined-payment-methods?
  (with-redefs [pricing-impl/list-payment-methods-customer (constantly {:bank-accounts [] :cards []})]
    (is (false? (t/has-defined-payment-methods? {}))))
  (with-redefs [pricing-impl/list-payment-methods-customer (constantly {:bank-accounts [:a] :cards []})]
    (is (true? (t/has-defined-payment-methods? {})))))

(deftest has-credit?
  (with-redefs [pricing-impl/customer->map (constantly {:balance -1.3})]
    (is (true? (t/has-credit? {}))))
  (with-redefs [pricing-impl/customer->map (constantly {:balance 1.3})]
    (is (false? (t/has-credit? {}))))
  (with-redefs [pricing-impl/customer->map (constantly {:balance 0})]
    (is (false? (t/has-credit? {}))))
  (with-redefs [pricing-impl/customer->map (constantly {})]
    (is (false? (t/has-credit? {})))))

(deftest can-pay?
  (with-redefs [t/has-defined-payment-methods? (constantly true)
                t/has-credit?                  (constantly false)]
    (is (true? (t/can-pay? {}))))
  (with-redefs [t/has-defined-payment-methods? (constantly false)
                t/has-credit?                  (constantly true)]
    (is (true? (t/can-pay? {}))))
  (with-redefs [t/has-defined-payment-methods? (constantly false)
                t/has-credit?                  (constantly false)]
    (is (false? (t/can-pay? {})))))

(deftest active-claim->customer
  (with-redefs [crud/query-as-admin (constantly [nil [:a]])]
    (is (= :a (t/active-claim->customer {})))))

(deftest active-claim->s-customer
  (with-redefs [t/active-claim->customer         (constantly {:customer-id 1})
                pricing-impl/retrieve-customer (constantly :a)]
    (is (= :a (t/active-claim->s-customer "")))))

(deftest active-claim->subscription
  (with-redefs [t/active-claim->customer (constantly {:id 1})
                crud/do-action-as-admin (constantly {:body :a})]
    (is (= :a (t/active-claim->subscription "")))))
