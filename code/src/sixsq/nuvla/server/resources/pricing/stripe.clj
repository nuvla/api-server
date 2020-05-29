(ns sixsq.nuvla.server.resources.pricing.stripe
  (:require [clojure.tools.logging :as log]
            [sixsq.nuvla.server.util.log :as logu])
  (:import
    (com.stripe Stripe)
    (com.stripe.exception StripeException)
    (com.stripe.model Customer Invoice PaymentMethod Plan
                      Product SetupIntent Subscription)))


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
       (logu/log-and-throw 400 (.getMessage e#)))))


(defn price->unit-float
  [price]
  (some-> price (/ 100) float))


(defn create-customer
  [customer-params]
  (try-catch-exception
    (Customer/create customer-params)))


(defn retrieve-customer
  [customer-id]
  (try-catch-exception
    (Customer/retrieve customer-id)))


(defn delete-discount-customer
  [customer]
  (try-catch-exception
    (.deleteDiscount customer)))


(defn update-customer
  [customer params]
  (try-catch-exception
    (.update customer params)))


(defn list-products
  [params]
  (try-catch-exception
    (Product/list params)))


(defn list-plans
  [params]
  (try-catch-exception
    (Plan/list params)))


(defn list-payment-methods
  [params]
  (try-catch-exception
    (PaymentMethod/list params)))


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


(defn get-upcoming-invoice
  [invoice-params]
  (try-catch-exception
    (Invoice/upcoming invoice-params)))


(defn list-invoices
  [params]
  (try-catch-exception
    (Invoice/list params)))


(defn retrieve-payment-method
  [payment-method-id]
  (try-catch-exception
    (PaymentMethod/retrieve payment-method-id)))


(defn detach-payment-method
  [s-payment-method]
  (try-catch-exception
    (.detach s-payment-method)))

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
  [obj]
  (.getCurrency obj))

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
  [obj]
  (.getAmount obj))

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

(defn get-client-secret
  [obj]
  (.getClientSecret obj))

(defn get-card
  [obj]
  (.getCard obj))

(defn get-sepa-debit
  [obj]
  (.getSepaDebit obj))

(defn get-brand
  [obj]
  (.getBrand obj))

(defn get-last4
  [obj]
  (.getLast4 obj))

(defn get-exp-month
  [obj]
  (.getExpMonth obj))

(defn get-exp-year
  [obj]
  (.getExpYear obj))

(defn get-invoice-settings
  [obj]
  (.getInvoiceSettings obj))

(defn get-default-payment-method
  [obj]
  (.getDefaultPaymentMethod obj))

(defn get-lines
  [obj]
  (.getLines obj))

(defn get-description
  [obj]
  (.getDescription obj))

(defn get-quantity
  [obj]
  (.getQuantity obj))

(defn get-period
  [obj]
  (.getPeriod obj))

(defn get-start
  [obj]
  (.getStart obj))

(defn get-end
  [obj]
  (.getEnd obj))

(defn get-created
  [obj]
  (.getCreated obj))

(defn get-due-date
  [obj]
  (.getDueDate obj))

(defn get-invoice-pdf
  [obj]
  (.getInvoicePdf obj))

(defn get-paid
  [obj]
  (.getPaid obj))

(defn get-total
  [obj]
  (.getTotal obj))

(defn get-data
  [obj]
  (.getData obj))


(defn get-number
  [obj]
  (.getNumber obj))


(defn get-address
  [obj]
  (.getAddress obj))


(defn get-city
  [obj]
  (.getCity obj))


(defn get-country
  [obj]
  (.getCountry obj))


(defn get-line1
  [obj]
  (.getLine1 obj))


(defn get-postal-code
  [obj]
  (.getPostalCode obj))


(defn get-discount
  [obj]
  (.getDiscount obj))


(defn get-coupon
  [obj]
  (.getCoupon obj))


(defn get-amount-off
  [obj]
  (.getAmountOff obj))


(defn get-duration
  [obj]
  (.getDuration obj))


(defn get-duration-in-months
  [obj]
  (.getDurationInMonths obj))


(defn get-percent-off
  [obj]
  (.getPercentOff obj))

(defn get-valid
  [obj]
  (.getValid obj))
