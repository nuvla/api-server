(ns sixsq.nuvla.server.resources.pricing.stripe
  (:require [sixsq.nuvla.server.util.log :as logu]
            [clojure.tools.logging :as log])
  (:import
    (com.stripe Stripe)
    (com.stripe.exception StripeException)
    (com.stripe.model Customer Subscription PaymentMethod Product Plan SetupIntent)))


(defn set-api-key!
  [api-key]
  (set! Stripe/apiKey api-key))


(defn get-api-key
  []
  Stripe/apiKey)


(defmacro try-catch-exception
  [& body]
  `(try
     ~@body
     (catch StripeException e#
       (logu/log-and-throw 500 (.getMessage e#)))))


(defn create-customer
  [customer-params]
  (try-catch-exception
    (Customer/create customer-params)))


(defn retrieve-customer
  [customer-id]
  (try-catch-exception
    (Customer/retrieve customer-id)))


(defn list-products
  [params]
  (try-catch-exception
    (Product/list params)))


(defn list-plans
  [params]
  (try-catch-exception
    (Plan/list params)))


(defn get-customer-subscriptions
  [customer]
  (try-catch-exception
    (.getSubscriptions customer)))


(defn collection-iterator
  [collection]
  (.autoPagingIterable collection))


(defn delete-customer
  [customer]
  (try-catch-exception
    (.delete customer)))


(defn create-subscription
  [subscription-params]
  (try-catch-exception
    (Subscription/create subscription-params)))


(defn create-setup-intent
  [setup-intent-params]
  (try-catch-exception
    (SetupIntent/create setup-intent-params)))


(defn create-payment-method
  [payment-method-params]
  (try-catch-exception
    (PaymentMethod/create payment-method-params)))


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

(defn get-trial-start
  [obj]
  (.getTrialStart obj))

(defn get-trial-end
  [obj]
  (.getTrialEnd obj))

(defn get-metadata
  [obj]
  (.getMetadata obj))

(defn get-name
  [obj]
  (.getName obj))

(defn get-active
  [obj]
  (.getActive obj))

(defn get-currency
  [plan]
  (.getCurrency plan))

(defn get-interval
  [plan]
  (.getInterval plan))

(defn get-usage-type
  [plan]
  (.getUsageType plan))

(defn get-billing-scheme
  [plan]
  (.getBillingScheme plan))

(defn get-trial-period-days
  [plan]
  (.getTrialPeriodDays plan))

(defn get-amount
  [plan]
  (.getAmount plan))

(defn get-aggregate-usage
  [plan]
  (.getAggregateUsage plan))

(defn get-tiers-mode
  [plan]
  (.getTiersMode plan))

(defn get-tiers
  [plan]
  (.getTiers plan))

(defn get-unit-amount
  [tier]
  (.getUnitAmount tier))

(defn get-up-to
  [tier]
  (.getUpTo tier))
