(ns com.sixsq.nuvla.pricing.payment-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [clojure.walk :as walk]
    [com.sixsq.nuvla.pricing.impl :as pricing-impl]
    [com.sixsq.nuvla.pricing.payment :as t]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]))

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
  (testing "should return customer"
    (with-redefs [crud/query-as-admin (constantly [nil [:a]])]
      (is (= :a (t/active-claim->customer "user/a")))))
  (testing "should return group customer"
    (with-redefs [crud/query-as-admin          (fn [_ params]
                                                 [nil [(case (:filter
                                                               (walk/postwalk
                                                                 (fn [x]
                                                                   (if (and (vector? x)
                                                                            (not= (first x) :filter))
                                                                     (last x)
                                                                     x))
                                                                 params))
                                                         "'group/a'" nil
                                                         "'group/root'" :root)]])
                  crud/retrieve-by-id-as-admin (constantly {:parents ["group/root" "group/parent"]})]
      (is (= :root (t/active-claim->customer "group/a"))))))

(deftest active-claim->s-customer
  (with-redefs [t/active-claim->customer       (constantly {:customer-id 1})
                pricing-impl/retrieve-customer (constantly :a)]
    (is (= :a (t/active-claim->s-customer "")))))

(deftest active-claim->subscription
  (with-redefs [t/active-claim->customer (constantly {:id 1})
                crud/do-action-as-admin  (constantly {:body :a})]
    (is (= :a (t/active-claim->subscription "")))))

(deftest tax-rates
  (let [customer-info-ch {:address {:country "CH"}}
        tax-ch           {:country "CH" :tax-rate-ids ["txr_ch1"]}
        catalog-ch       {:taxes [tax-ch]}
        catalog-ch-fr    {:taxes [{:country "FR" :tax-rate-ids ["txr_fr1" ["txr_fr2"]]}
                                  tax-ch]}]
    (are [expected customer-info catalog]
      (= expected (t/tax-rates customer-info catalog))

      []
      customer-info-ch
      {}

      ["txr_ch1"]
      customer-info-ch
      catalog-ch

      ["txr_ch1"]
      customer-info-ch
      catalog-ch-fr

      ["txr_fr1" ["txr_fr2"]]
      {:address {:country "FR"}}
      catalog-ch-fr)))

(deftest get-catalog
  (with-redefs [crud/retrieve-by-id-as-admin (constantly {})]
    (is (= {} (t/get-catalog)))))
