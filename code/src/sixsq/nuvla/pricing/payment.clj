(ns sixsq.nuvla.pricing.payment
  (:require [sixsq.nuvla.pricing.impl :as pricing-impl]
            [sixsq.nuvla.server.util.response :as r]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.db.filter.parser :as parser]))

(defn has-defined-payment-methods?
  [s-customer]
  (let [payment-methods (pricing-impl/list-payment-methods-customer s-customer)]
    (pos?
      (+ (count (:cards payment-methods))
         (count (:bank-accounts payment-methods))))))

(defn has-credit?
  [s-customer]
  (boolean
    (some-> s-customer
            pricing-impl/customer->map
            :balance
            neg?)))

(defn can-pay?
  [s-customer]
  (or
    (has-defined-payment-methods? s-customer)
    (has-credit? s-customer)))

(defn active-claim->customer
  [active-claim]
  (-> (crud/query-as-admin
        "customer"
        {:cimi-params
         {:filter (parser/parse-cimi-filter
                    (format "parent='%s'" active-claim))}})
      second
      first))

(defn active-claim->s-customer
  [active-claim]
  (-> active-claim
      active-claim->customer
     :customer-id
     pricing-impl/retrieve-customer))

(defn active-claim->subscription
  [active-claim]
  (some-> active-claim
          active-claim->customer
          :id
          (crud/do-action-as-admin "get-subscription")
          :body))

(defn throw-payment-required
  []
  (throw (r/ex-response
           "Valid subscription and payment method are needed!" 402)))
