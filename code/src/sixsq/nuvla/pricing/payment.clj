(ns sixsq.nuvla.pricing.payment
  (:require [sixsq.nuvla.pricing.impl :as pricing-impl]))

(defn count-payment-methods
  [{:keys [cards bank-accounts]}]
  (+ (count cards) (count bank-accounts)))


(defn has-defined-payment-methods?
  [{:keys [customer-id] :as _customer}]
  (-> customer-id
      pricing-impl/retrieve-customer
      pricing-impl/list-payment-methods-customer
      count-payment-methods
      pos?))
