(ns sixsq.nuvla.pricing.impl
  (:require [sixsq.nuvla.pricing.protocol :as protocol]))


(def ^:dynamic *impl* nil)


(defn set-impl!
  [impl]
  (alter-var-root #'*impl* (constantly impl)))

(defn set-api-key! [api-key]
  (protocol/set-api-key! *impl* api-key))

(defn get-api-key []
  (protocol/get-api-key *impl*))

(defn create-customer [params]
  (protocol/create-customer *impl* params))

(defn retrieve-customer [customer-id]
  (protocol/retrieve-customer *impl* customer-id))

(defn update-customer [customer params]
  (protocol/update-customer *impl* customer params))

(defn delete-customer [customer]
  (protocol/delete-customer *impl* customer))

(defn delete-discount [customer]
  (protocol/delete-discount *impl* customer))

(defn get-customer-subscriptions [customer]
  (protocol/get-customer-subscriptions *impl* customer))

(defn list-subscriptions [params]
  (protocol/list-subscriptions *impl* params))

(defn create-subscription [params]
  (protocol/create-subscription *impl* params))

(defn list-products [products]
  (protocol/list-products *impl* products))

(defn list-prices [params]
  (protocol/list-prices *impl* params))

(defn retrieve-subscription [subscription-id]
  (protocol/retrieve-subscription *impl* subscription-id))

(defn cancel-subscription [subscription params]
  (protocol/cancel-subscription *impl* subscription params))

(defn collection-iterator [collection]
  (protocol/collection-iterator *impl* collection))

(defn create-setup-intent [params]
  (protocol/create-setup-intent *impl* params))

(defn list-invoices [params]
  (protocol/list-invoices *impl* params))

(defn retrieve-payment-method [payment-method-id]
  (protocol/retrieve-payment-method *impl* payment-method-id))

(defn detach-payment-method [payment-method]
  (protocol/detach-payment-method *impl* payment-method))

(defn oauth-token [params]
  (protocol/oauth-token *impl* params))

(defn login-link-create-on-account [account-id]
  (protocol/login-link-create-on-account *impl* account-id))

(defn create-price [params]
  (protocol/create-price *impl* params))

(defn retrieve-price [price-id]
  (protocol/retrieve-price *impl* price-id))

(defn get-id [obj]
  (protocol/get-id *impl* obj))

(defn get-deleted [obj]
  (protocol/get-deleted *impl* obj))

(defn get-status [obj]
  (protocol/get-status *impl* obj))

(defn get-start-date [obj]
  (protocol/get-start-date *impl* obj))

(defn get-current-period-end [obj]
  (protocol/get-current-period-end *impl* obj))

(defn get-current-period-start [obj]
  (protocol/get-current-period-start *impl* obj))

(defn get-trial-start [obj]
  (protocol/get-trial-start *impl* obj))

(defn get-trial-end [obj]
  (protocol/get-trial-end *impl* obj))

(defn get-metadata [obj]
  (protocol/get-metadata *impl* obj))

(defn get-name [obj]
  (protocol/get-name *impl* obj))

(defn get-active [obj]
  (protocol/get-active *impl* obj))

(defn get-currency [obj]
  (protocol/get-currency *impl* obj))

(defn get-interval [obj]
  (protocol/get-interval *impl* obj))

(defn get-usage-type [obj]
  (protocol/get-usage-type *impl* obj))

(defn get-billing-scheme [obj]
  (protocol/get-billing-scheme *impl* obj))

(defn get-trial-period-days [obj]
  (protocol/get-trial-period-days *impl* obj))

(defn get-amount [obj]
  (protocol/get-amount *impl* obj))

(defn get-recurring [obj]
  (protocol/get-recurring *impl* obj))

(defn get-aggregate-usage [obj]
  (protocol/get-aggregate-usage *impl* obj))

(defn get-tiers-mode [obj]
  (protocol/get-tiers-mode *impl* obj))

(defn get-tiers [obj]
  (protocol/get-tiers *impl* obj))

(defn get-unit-amount [obj]
  (protocol/get-unit-amount *impl* obj))

(defn get-up-to [obj]
  (protocol/get-up-to *impl* obj))

(defn get-client-secret [obj]
  (protocol/get-client-secret *impl* obj))

(defn get-card [obj]
  (protocol/get-card *impl* obj))

(defn get-sepa-debit [obj]
  (protocol/get-sepa-debit *impl* obj))

(defn get-brand [obj]
  (protocol/get-brand *impl* obj))

(defn get-last4 [obj]
  (protocol/get-last4 *impl* obj))

(defn get-exp-month [obj]
  (protocol/get-exp-month *impl* obj))

(defn get-exp-year [obj]
  (protocol/get-exp-year *impl* obj))

(defn get-invoice-settings [obj]
  (protocol/get-invoice-settings *impl* obj))

(defn get-default-payment-method [obj]
  (protocol/get-default-payment-method *impl* obj))

(defn get-lines [obj]
  (protocol/get-lines *impl* obj))

(defn get-description [obj]
  (protocol/get-description *impl* obj))

(defn get-quantity [obj]
  (protocol/get-quantity *impl* obj))

(defn get-period [obj]
  (protocol/get-period *impl* obj))

(defn get-start [obj]
  (protocol/get-start *impl* obj))

(defn get-end [obj]
  (protocol/get-end *impl* obj))

(defn get-created [obj]
  (protocol/get-created *impl* obj))

(defn get-due-date [obj]
  (protocol/get-due-date *impl* obj))

(defn get-invoice-pdf [obj]
  (protocol/get-invoice-pdf *impl* obj))

(defn get-paid [obj]
  (protocol/get-paid *impl* obj))

(defn get-total [obj]
  (protocol/get-total *impl* obj))

(defn get-subtotal [obj]
  (protocol/get-subtotal *impl* obj))

(defn get-data [obj]
  (protocol/get-data *impl* obj))

(defn get-number [obj]
  (protocol/get-number *impl* obj))

(defn get-address [obj]
  (protocol/get-address *impl* obj))

(defn get-city [obj]
  (protocol/get-city *impl* obj))

(defn get-country [obj]
  (protocol/get-country *impl* obj))

(defn get-line1 [obj]
  (protocol/get-line1 *impl* obj))

(defn get-postal-code [obj]
  (protocol/get-postal-code *impl* obj))

(defn get-discount [obj]
  (protocol/get-discount *impl* obj))

(defn get-coupon [obj]
  (protocol/get-coupon *impl* obj))

(defn get-amount-off [obj]
  (protocol/get-amount-off *impl* obj))

(defn get-duration [obj]
  (protocol/get-duration *impl* obj))

(defn get-duration-in-months [obj]
  (protocol/get-duration-in-months *impl* obj))

(defn get-percent-off [obj]
  (protocol/get-percent-off *impl* obj))

(defn get-valid [obj]
  (protocol/get-valid *impl* obj))

(defn get-user-id [obj]
  (protocol/get-user-id *impl* obj))

(defn get-url [obj]
  (protocol/get-url *impl* obj))

(defn get-product [obj]
  (protocol/get-product *impl* obj))

(defn coupon->map [coupon]
  (protocol/coupon->map *impl* coupon))

(defn customer->map [customer]
  (protocol/customer->map *impl* customer))

(defn subscription->map [subscription]
  (protocol/subscription->map *impl* subscription))

(defn payment-method-card->map [payment-method]
  (protocol/payment-method-card->map *impl* payment-method))

(defn payment-method-sepa->map [payment-method]
  (protocol/payment-method-sepa->map *impl* payment-method))

(defn setup-intent->map [setup-intent]
  (protocol/setup-intent->map *impl* setup-intent))

(defn invoice-line-item->map [invoice-line-item]
  (protocol/invoice-line-item->map *impl* invoice-line-item))

(defn invoice->map [invoice extend]
  (protocol/invoice->map *impl* invoice extend))

(defn price->map [price]
  (protocol/price->map *impl* price))

(defn get-default-payment-method-customer [customer]
  (protocol/get-default-payment-method-customer *impl* customer))

(defn list-payment-methods [params]
  (protocol/list-payment-methods *impl* params))

(defn list-payment-methods-customer [customer]
  (protocol/list-payment-methods-customer *impl* customer))

(defn get-upcoming-invoice [params]
  (protocol/get-upcoming-invoice *impl* params))

(defn get-upcoming-invoice-subscription-id [subscription-id]
  (protocol/get-upcoming-invoice-subscription-id *impl* subscription-id))

(defn price->unit-float [price]
  (protocol/price->unit-float *impl* price))
