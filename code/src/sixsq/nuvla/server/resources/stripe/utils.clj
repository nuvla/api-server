(ns sixsq.nuvla.server.resources.stripe.utils
  (:require [clojure.tools.logging :as log]
            [sixsq.nuvla.server.util.log :as logu])
  (:import
    (com.stripe Stripe)
    (com.stripe.exception StripeException)
    (com.stripe.model Customer Subscription PaymentMethod Product)))


(set! Stripe/apiKey "")

(defn create-customer
  [customer-params]
  (try
    (Customer/create customer-params)
    (catch StripeException e
      (logu/log-and-throw 500 (.getMessage e)))))


(defn retrieve-customer
  [customer-id]
  (try
    (Customer/retrieve customer-id)
    (catch StripeException e
      (logu/log-and-throw 500 (.getMessage e)))))


(defn list-products
  []
  (Product/list {}))


(defn get-customer-subscriptions
  [customer]
  (.getSubscriptions customer))


(defn collection-iterator
  [collection]
  (.autoPagingIterable collection))


(defn delete-customer
  [customer]
  (try
    (.delete customer)
    (catch StripeException e
      (logu/log-and-throw 500 (.getMessage e)))))


(defn create-subscription
  [subscription-params]
  (try
    (Subscription/create subscription-params)
    (catch StripeException e
      (logu/log-and-throw 500 (.getMessage e)))))


(defn create-payment-method
  [payment-method-params]
  (try
    (PaymentMethod/create payment-method-params)
    (catch StripeException e
      (logu/log-and-throw 500 (.getMessage e)))))


(defn get-id
  [obj]
  (.getId obj))

(defn get-deleted
  [obj]
  (.getDeleted obj))

(defn get-status
  [obj]
  (.getStatus obj))

(defn get-start-date
  [obj]
  (.getStartDate obj))

(defn get-current-period-end
  [obj]
  (.getCurrentPeriodEnd obj))

(defn get-current-period-start
  [obj]
  (.getCurrentPeriodStart obj))

(defn get-metadata
  [obj]
  (.getMetadata obj))

(defn get-name
  [obj]
  (.getName obj))

