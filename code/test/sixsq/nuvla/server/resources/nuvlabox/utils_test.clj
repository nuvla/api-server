(ns sixsq.nuvla.server.resources.nuvlabox.utils-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as t]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.auth.acl-resource :as a])
  (:import (clojure.lang ExceptionInfo)))


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
  (testing "customer active and has payment"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly true)]
      (is (= (t/throw-when-payment-required {}) {}))))
  (testing "customer is trialing"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly false)]
      (is (= (t/throw-when-payment-required {}) {}))))
  (testing "customer past_due and has not payment"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "past_due"})
                  payment/can-pay?                   (constantly false)]
      (is (thrown-with-msg? ExceptionInfo
                            #"Valid subscription and payment method are needed!"
                            (t/throw-when-payment-required {}))))))
;; Nuvlabox case applied for actions: Nuvlabox [add | unsuspend]
;; authorize WHEN
;;  stripe is disabled
;;      OR
;;  admin user is doing the action
;;      OR
;;  customer is in state [active | past_due] AND (has payment-method OR credit)
;;      OR
;;  customer is in state [trialing]
;; Refuse all other cases