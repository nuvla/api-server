(ns sixsq.nuvla.pricing.payment
  (:require [clojure.string :as str]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.pricing.impl :as pricing-impl]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.util.response :as r]))

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


(defn- get-customer [claim]
  (-> (crud/query-as-admin "customer"
                           {:cimi-params
                            {:filter (parser/parse-cimi-filter
                                       (format "parent='%s'" claim))}})
      second
      first))

(defn active-claim->customer
  [active-claim]
  (or (get-customer active-claim)
      (and (str/starts-with? active-claim "group/")
           (some-> (crud/retrieve-by-id-as-admin active-claim)
                   :parents
                   first
                   get-customer))))

(defn active-claim->s-customer
  [active-claim]
  (some-> active-claim
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


(defn get-catalog
  []
  (crud/retrieve-by-id-as-admin "catalog/catalog"))

(defn tax-rates
  [{{customer-country :country} :address :as _customer-info}
   {:keys [taxes] :as _catalog}]
  (or (some (fn [{:keys [country tax-rate-ids]}]
              (when (= country customer-country)
                tax-rate-ids)) taxes)
      []))
