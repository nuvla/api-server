(ns sixsq.nuvla.pricing.stripe.stripe
  (:require
    [sixsq.nuvla.pricing.protocol :refer [Pricing]]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.time :as time])
  (:import
    (com.stripe Stripe)
    (com.stripe.exception StripeException)
    (com.stripe.model Customer Invoice LoginLink PaymentMethod Price
                      Product SetupIntent Subscription)
    (com.stripe.net OAuth)
    (java.util HashMap)))


(defmacro try-catch-exception
  [& body]
  `(try
     ~@body
     (catch StripeException e#
       (if (#{"invoice_upcoming_none"} (.getCode e#))
         nil
         (logu/log-and-throw 400 (.getMessage e#))
         ))))


(defn s-price->unit-float
  [price]
  (some-> price (/ 100) float))


(defn s-collection-iterator
  [collection]
  (.autoPagingIterable collection))


(defn s-set-api-key!
  [api-key]
  (set! Stripe/apiKey api-key))


(defn s-get-api-key
  []
  Stripe/apiKey)


(defn s-create-customer
  [customer-params]
  (try-catch-exception
    (Customer/create ^HashMap customer-params)))


(defn s-retrieve-customer
  [customer-id]
  (try-catch-exception
    (Customer/retrieve customer-id)))


(defn s-delete-discount-customer
  [customer]
  (try-catch-exception
    (.deleteDiscount customer)))


(defn s-update-customer
  [customer params]
  (try-catch-exception
    (.update customer params)))


(defn s-list-products
  [params]
  (try-catch-exception
    (Product/list ^HashMap params)))


(defn s-list-prices
  [params]
  (try-catch-exception
    (Price/list ^HashMap params)))


(defn s-get-customer-subscriptions
  [customer]
  (try-catch-exception
    (.getSubscriptions customer)))


(defn s-delete-customer
  [customer]
  (try-catch-exception
    (.delete customer)))


(defn s-create-subscription
  [subscription-params]
  (try-catch-exception
    (Subscription/create ^HashMap subscription-params)))


(defn s-retrieve-subscription
  [subscription-id]
  (try-catch-exception
    (Subscription/retrieve subscription-id)))

(defn s-cancel-subscription
  [subscription params]
  (try-catch-exception
    (.cancel subscription params)))


(defn s-create-setup-intent
  [setup-intent-params]
  (try-catch-exception
    (SetupIntent/create ^HashMap setup-intent-params)))


(defn s-list-invoices
  [params]
  (try-catch-exception
    (Invoice/list ^HashMap params)))


(defn s-retrieve-payment-method
  [payment-method-id]
  (try-catch-exception
    (PaymentMethod/retrieve payment-method-id)))


(defn s-detach-payment-method
  [payment-method]
  (try-catch-exception
    (.detach payment-method)))

(defn s-oauth-token
  [params]
  (try-catch-exception
    (OAuth/token params nil)))


(defn s-login-link-create-on-account
  [account-id]
  (try-catch-exception
    (LoginLink/createOnAccount ^String account-id {} nil)))


(defn s-create-price
  [price-params]
  (try-catch-exception
    (Price/create ^HashMap price-params)))


(defn s-retrieve-price
  [price-id]
  (try-catch-exception
    (Price/retrieve price-id)))


(defn s-get-id
  [obj]
  (.getId obj))

(defn s-get-deleted
  [obj]
  (.getDeleted obj))

(defn s-get-status
  [obj]
  (.getStatus obj))

(defn s-get-start-date
  [obj]
  (.getStartDate obj))

(defn s-get-current-period-end
  [obj]
  (.getCurrentPeriodEnd obj))

(defn s-get-current-period-start
  [obj]
  (.getCurrentPeriodStart obj))

(defn s-get-trial-start
  [obj]
  (.getTrialStart obj))

(defn s-get-trial-end
  [obj]
  (.getTrialEnd obj))

(defn s-get-metadata
  [obj]
  (.getMetadata obj))

(defn s-get-name
  [obj]
  (.getName obj))

(defn s-get-active
  [obj]
  (.getActive obj))

(defn s-get-currency
  [obj]
  (.getCurrency obj))

(defn s-get-interval
  [obj]
  (.getInterval obj))

(defn s-get-usage-type
  [obj]
  (.getUsageType obj))

(defn s-get-billing-scheme
  [obj]
  (.getBillingScheme obj))

(defn s-get-trial-period-days
  [obj]
  (.getTrialPeriodDays obj))

(defn s-get-amount
  [obj]
  (.getAmount obj))

(defn s-get-recurring
  [obj]
  (.getRecurring obj))

(defn s-get-aggregate-usage
  [obj]
  (.getAggregateUsage obj))

(defn s-get-tiers-mode
  [obj]
  (.getTiersMode obj))

(defn s-get-tiers
  [obj]
  (.getTiers obj))

(defn s-get-unit-amount
  [obj]
  (.getUnitAmount obj))

(defn s-get-up-to
  [obj]
  (.getUpTo obj))

(defn s-get-client-secret
  [obj]
  (.getClientSecret obj))

(defn s-get-card
  [obj]
  (.getCard obj))

(defn s-get-sepa-debit
  [obj]
  (.getSepaDebit obj))

(defn s-get-brand
  [obj]
  (.getBrand obj))

(defn s-get-last4
  [obj]
  (.getLast4 obj))

(defn s-get-exp-month
  [obj]
  (.getExpMonth obj))

(defn s-get-exp-year
  [obj]
  (.getExpYear obj))

(defn s-get-invoice-settings
  [obj]
  (.getInvoiceSettings obj))

(defn s-get-default-payment-method
  [obj]
  (.getDefaultPaymentMethod obj))

(defn s-get-lines
  [obj]
  (.getLines obj))

(defn s-get-description
  [obj]
  (.getDescription obj))

(defn s-get-quantity
  [obj]
  (.getQuantity obj))

(defn s-get-period
  [obj]
  (.getPeriod obj))

(defn s-get-start
  [obj]
  (.getStart obj))

(defn s-get-end
  [obj]
  (.getEnd obj))

(defn s-get-created
  [obj]
  (.getCreated obj))

(defn s-get-due-date
  [obj]
  (.getDueDate obj))

(defn s-get-invoice-pdf
  [obj]
  (.getInvoicePdf obj))

(defn s-get-paid
  [obj]
  (.getPaid obj))

(defn s-get-total
  [obj]
  (.getTotal obj))

(defn s-get-subtotal
  [obj]
  (.getSubtotal obj))

(defn s-get-data
  [obj]
  (.getData obj))


(defn s-get-number
  [obj]
  (.getNumber obj))


(defn s-get-address
  [obj]
  (.getAddress obj))


(defn s-get-city
  [obj]
  (.getCity obj))


(defn s-get-country
  [obj]
  (.getCountry obj))


(defn s-get-line1
  [obj]
  (.getLine1 obj))


(defn s-get-postal-code
  [obj]
  (.getPostalCode obj))


(defn s-get-discount
  [obj]
  (.getDiscount obj))


(defn s-get-coupon
  [obj]
  (.getCoupon obj))


(defn s-get-amount-off
  [obj]
  (.getAmountOff obj))


(defn s-get-duration
  [obj]
  (.getDuration obj))


(defn s-get-duration-in-months
  [obj]
  (.getDurationInMonths obj))


(defn s-get-percent-off
  [obj]
  (.getPercentOff obj))


(defn s-get-valid
  [obj]
  (.getValid obj))


(defn s-get-user-id
  [obj]
  (.getStripeUserId obj))


(defn s-get-url
  [obj]
  (.getUrl obj))


(defn s-get-product
  [obj]
  (.getProduct obj))


(defn s-coupon->map
  [coupon]
  {:id                 (s-get-id coupon)
   :name               (s-get-name coupon)
   :amount-off         (-> coupon
                           s-get-amount-off
                           s-price->unit-float)
   :currency           (s-get-currency coupon)
   :duration           (s-get-duration coupon)
   :duration-in-months (s-get-duration-in-months coupon)
   :percent-off        (s-get-percent-off coupon)
   :valid              (s-get-valid coupon)})


(defn s-customer->map
  [customer]
  (let [s-address (s-get-address customer)
        s-coupon  (some-> customer s-get-discount s-get-coupon)]
    (cond-> {:fullname (s-get-name customer)
             :address  {:street-address (s-get-line1 s-address)
                        :city           (s-get-city s-address)
                        :country        (s-get-country s-address)
                        :postal-code    (s-get-postal-code s-address)}}
            s-coupon (assoc :coupon (s-coupon->map s-coupon)))))

(defn s-subscription->map
  [subscription]
  (cond-> {:id                   (s-get-id subscription)
           :status               (s-get-status subscription)
           :start-date           (some-> (s-get-start-date subscription)
                                         time/unix-timestamp->str)
           :current-period-start (some-> (s-get-current-period-start subscription)
                                         time/unix-timestamp->str)
           :current-period-end   (some-> (s-get-current-period-end subscription)
                                         time/unix-timestamp->str)
           :trial-start          (some-> (s-get-trial-start subscription)
                                         time/unix-timestamp->str)
           :trial-end            (some-> (s-get-trial-end subscription)
                                         time/unix-timestamp->str)}))

(defn s-payment-method-card->map
  [payment-method]
  (let [card (s-get-card payment-method)]
    {:payment-method (s-get-id payment-method)
     :brand          (s-get-brand card)
     :last4          (s-get-last4 card)
     :exp-month      (s-get-exp-month card)
     :exp-year       (s-get-exp-year card)}))


(defn s-payment-method-sepa->map
  [payment-method]
  (let [sepa-debit (s-get-sepa-debit payment-method)]
    {:payment-method (s-get-id payment-method)
     :last4          (s-get-last4 sepa-debit)}))


(defn s-setup-intent->map
  [setup-intent]
  {:client-secret (s-get-client-secret setup-intent)})


(defn s-invoice-line-item->map
  [invoice-line-item]
  {:amount      (s-price->unit-float (s-get-amount invoice-line-item))
   :currency    (s-get-currency invoice-line-item)
   :description (s-get-description invoice-line-item)
   :period      (let [p (s-get-period invoice-line-item)]
                  {:start (some-> (s-get-start p)
                                  time/unix-timestamp->str)

                   :end   (some-> (s-get-end p)
                                  time/unix-timestamp->str)})
   :quantity    (s-get-quantity invoice-line-item)})


(defn s-invoice->map
  [invoice extend]
  (let [s-coupon (some-> invoice s-get-discount s-get-coupon)]
    (cond-> {:id          (s-get-id invoice)
             :number      (s-get-number invoice)
             :created     (some-> (s-get-created invoice)
                                  time/unix-timestamp->str)
             :currency    (s-get-currency invoice)
             :due-date    (some-> (s-get-due-date invoice)
                                  time/unix-timestamp->str)
             :invoice-pdf (s-get-invoice-pdf invoice)
             :paid        (s-get-paid invoice)
             :status      (s-get-status invoice)
             :subtotal    (s-price->unit-float (s-get-subtotal invoice))
             :total       (s-price->unit-float (s-get-total invoice))}
            s-coupon (assoc :discount {:coupon (s-coupon->map s-coupon)})
            extend (assoc :lines (->> invoice
                                      s-get-lines
                                      s-collection-iterator
                                      (map s-invoice-line-item->map))))))


(defn s-price->map
  [price]
  {:product-id (s-get-product price)})


(defn s-get-default-payment-method-for-customer
  [customer]
  (-> customer
      s-get-invoice-settings
      s-get-default-payment-method))


(defn s-list-payment-methods
  [customer]
  (let [id            (s-get-id customer)
        cards         (->> {"customer" id
                            "type"     "card"}
                           (s-list-payment-methods)
                           (s-collection-iterator)
                           (map s-payment-method-card->map))
        bank-accounts (->> {"customer" id
                            "type"     "sepa_debit"}
                           (s-list-payment-methods)
                           (s-collection-iterator)
                           (map s-payment-method-sepa->map))]
    {:cards                  cards
     :bank-accounts          bank-accounts
     :default-payment-method (s-get-default-payment-method-for-customer customer)}))


(defn s-get-upcoming-invoice
  [subscription-id]
  (some-> {"subscription" subscription-id}
          s-get-upcoming-invoice
          (s-invoice->map true)))


(deftype StripeType []
  Pricing
  (set-api-key! [_ api-key]
    (s-set-api-key! api-key))
  (get-api-key [_]
    (s-get-api-key))
  (create-customer [_ customer-params]
    (s-create-customer customer-params))
  (retrieve-customer [_ customer-id]
    (s-retrieve-customer customer-id))
  (update-customer [_ customer params]
    (s-update-customer customer params))
  (delete-customer [_ customer]
    (s-delete-customer customer))
  (delete-discount-customer [_ customer]
    (s-delete-discount-customer customer))
  (get-customer-subscriptions [_ customer]
    (s-get-customer-subscriptions customer))
  (create-subscription [_ subscription-params]
    (s-create-subscription subscription-params))
  (list-products [_ products]
    (s-list-products products))
  (list-prices [_ params]
    (s-list-prices params))
  (retrieve-subscription [_ subscription-id]
    (s-retrieve-subscription subscription-id))
  (cancel-subscription [_ subscription params]
    (s-cancel-subscription subscription params))
  (collection-iterator [_ collection]
    (s-collection-iterator collection))
  (create-setup-intent [_ setup-intent-params]
    (s-create-setup-intent setup-intent-params))
  (list-invoices [_ params]
    (s-list-invoices params))
  (retrieve-payment-method [_ payment-method-id]
    (s-retrieve-payment-method payment-method-id))
  (detach-payment-method [_ payment-method]
    (s-detach-payment-method payment-method))
  (oauth-token [_ params]
    (s-oauth-token params))
  (login-link-create-on-account [_ account-id]
    (s-login-link-create-on-account account-id))
  (create-price [_ price-params]
    (s-create-price price-params))
  (retrieve-price [_ price-id]
    (s-retrieve-price price-id))
  (get-id [_ obj]
    (s-get-id obj))
  (get-deleted [_ obj]
    (s-get-deleted obj))
  (get-status [_ obj]
    (s-get-status obj))
  (get-start-date [_ obj]
    (s-get-start-date obj))
  (get-current-period-end [_ obj]
    (s-get-current-period-end obj))
  (get-current-period-start [_ obj]
    (s-get-current-period-start obj))
  (get-trial-start [_ obj]
    (s-get-trial-start obj))
  (get-trial-end [_ obj]
    (s-get-trial-end obj))
  (get-metadata [_ obj]
    (s-get-metadata obj))
  (get-name [_ obj]
    (s-get-name obj))
  (get-active [_ obj]
    (s-get-active obj))
  (get-currency [_ obj]
    (s-get-currency obj))
  (get-interval [_ obj]
    (s-get-interval obj))
  (get-usage-type [_ obj]
    (s-get-usage-type obj))
  (get-billing-scheme [_ obj]
    (s-get-billing-scheme obj))
  (get-trial-period-days [_ obj]
    (s-get-trial-period-days obj))
  (get-amount [_ obj]
    (s-get-amount obj))
  (get-recurring [_ obj]
    (s-get-recurring obj))
  (get-aggregate-usage [_ obj]
    (s-get-aggregate-usage obj))
  (get-tiers-mode [_ obj]
    (s-get-tiers-mode obj))
  (get-tiers [_ obj]
    (s-get-tiers obj))
  (get-unit-amount [_ obj]
    (s-get-unit-amount obj))
  (get-up-to [_ obj]
    (s-get-up-to obj))
  (get-client-secret [_ obj]
    (s-get-client-secret obj))
  (get-card [_ obj]
    (s-get-card obj))
  (get-sepa-debit [_ obj]
    (s-get-sepa-debit obj))
  (get-brand [_ obj]
    (s-get-brand obj))
  (get-last4 [_ obj]
    (s-get-last4 obj))
  (get-exp-month [_ obj]
    (s-get-exp-month obj))
  (get-exp-year [_ obj]
    (s-get-exp-year obj))
  (get-invoice-settings [_ obj]
    (s-get-invoice-settings obj))
  (get-default-payment-method [_ obj]
    (s-get-default-payment-method obj))
  (get-lines [_ obj]
    (s-get-lines obj))
  (get-description [_ obj]
    (s-get-description obj))
  (get-quantity [_ obj]
    (s-get-quantity obj))
  (get-period [_ obj]
    (s-get-period obj))
  (get-start [_ obj]
    (s-get-start obj))
  (get-end [_ obj]
    (s-get-end obj))
  (get-created [_ obj]
    (s-get-created obj))
  (get-due-date [_ obj]
    (s-get-due-date obj))
  (get-invoice-pdf [_ obj]
    (s-get-invoice-pdf obj))
  (get-paid [_ obj]
    (s-get-paid obj))
  (get-total [_ obj]
    (s-get-total obj))
  (get-subtotal [_ obj]
    (s-get-subtotal obj))
  (get-data [_ obj]
    (s-get-data obj))
  (get-number [_ obj]
    (s-get-number obj))
  (get-address [_ obj]
    (s-get-address obj))
  (get-city [_ obj]
    (s-get-city obj))
  (get-country [_ obj]
    (s-get-country obj))
  (get-line1 [_ obj]
    (s-get-line1 obj))
  (get-postal-code [_ obj]
    (s-get-postal-code obj))
  (get-discount [_ obj]
    (s-get-discount obj))
  (get-coupon [_ obj]
    (s-get-coupon obj))
  (get-amount-off [_ obj]
    (s-get-amount-off obj))
  (get-duration [_ obj]
    (s-get-duration obj))
  (get-duration-in-months [_ obj]
    (s-get-duration-in-months obj))
  (get-percent-off [_ obj]
    (s-get-percent-off obj))
  (get-valid [_ obj]
    (s-get-valid obj))
  (get-user-id [_ obj]
    (s-get-user-id obj))
  (get-url [_ obj]
    (s-get-url obj))
  (get-product [_ obj]
    (s-get-product obj))
  (coupon->map [_ coupon]
    (s-coupon->map coupon))
  (customer->map [_ customer]
    (s-customer->map customer))
  (subscription->map [_ subscription]
    (s-subscription->map subscription))
  (payment-method-card->map [_ payment-method]
    (s-payment-method-card->map payment-method))
  (payment-method-sepa->map [_ payment-method]
    (s-payment-method-sepa->map payment-method))
  (setup-intent->map [_ setup-intent]
    (s-setup-intent->map setup-intent))
  (invoice-line-item->map [_ invoice-line-item]
    (s-invoice-line-item->map invoice-line-item))
  (invoice->map [_ invoice extend]
    (s-invoice->map invoice extend))
  (price->map [_ price]
    (s-price->map price))
  (get-default-payment-method-for-customer [_ customer]
    (s-get-default-payment-method-for-customer customer))
  (list-payment-methods [_ customer]
    (s-list-payment-methods customer))
  (get-upcoming-invoice [_ subscription-id]
    (s-get-upcoming-invoice subscription-id))
  (price->unit-float [_ price]
    (s-price->unit-float price)))


(defn load
  []
  (->StripeType))
