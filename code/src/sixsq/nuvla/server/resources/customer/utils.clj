(ns sixsq.nuvla.server.resources.customer.utils
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.tools.logging :as log]
    [expound.alpha :as expound]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.pricing :as pricing]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))

(def ^:const customer-info-action "customer-info")
(def ^:const update-customer-action "update-customer")
(def ^:const get-subscription-action "get-subscription")
(def ^:const create-subscription-action "create-subscription")
(def ^:const create-setup-intent-action "create-setup-intent")
(def ^:const list-payment-methods-action "list-payment-methods")
(def ^:const set-default-payment-method-action "set-default-payment-method")
(def ^:const detach-payment-method-action "detach-payment-method")
(def ^:const upcoming-invoice-action "upcoming-invoice")
(def ^:const list-invoices-action "list-invoices")
(def ^:const add-coupon-action "add-coupon")
(def ^:const delete-coupon-action "remove-coupon")


(defn s-customer->customer-map
  [s-customer]
  (let [s-address (stripe/get-address s-customer)
        s-coupon  (some-> s-customer stripe/get-discount stripe/get-coupon)]
    (cond-> {:fullname (stripe/get-name s-customer)
             :address  {:street-address (stripe/get-line1 s-address)
                        :city           (stripe/get-city s-address)
                        :country        (stripe/get-country s-address)
                        :postal-code    (stripe/get-postal-code s-address)}}
            s-coupon (assoc :coupon {:id                 (stripe/get-id s-coupon)
                                     :name               (stripe/get-name s-coupon)
                                     :amount-off         (-> s-coupon
                                                             stripe/get-amount-off
                                                             stripe/price->unit-float)
                                     :currency           (stripe/get-currency s-coupon)
                                     :duration           (stripe/get-duration s-coupon)
                                     :duration-in-months (stripe/get-duration-in-months s-coupon)
                                     :percent-off        (stripe/get-percent-off s-coupon)
                                     :valid              (stripe/get-valid s-coupon)}))))


(defn s-subscription->map
  [s-subscription]
  (cond-> {:id                   (stripe/get-id s-subscription)
           :status               (stripe/get-status s-subscription)
           :start-date           (some-> (stripe/get-start-date s-subscription)
                                         time/unix-timestamp->str)
           :current-period-start (some-> (stripe/get-current-period-start s-subscription)
                                         time/unix-timestamp->str)
           :current-period-end   (some-> (stripe/get-current-period-end s-subscription)
                                         time/unix-timestamp->str)
           :trial-start          (some-> (stripe/get-trial-start s-subscription)
                                         time/unix-timestamp->str)
           :trial-end            (some-> (stripe/get-trial-end s-subscription)
                                         time/unix-timestamp->str)}))


(defn s-payment-method-card->map
  [s-payment-method]
  (let [card (stripe/get-card s-payment-method)]
    {:payment-method (stripe/get-id s-payment-method)
     :brand          (stripe/get-brand card)
     :last4          (stripe/get-last4 card)
     :exp-month      (stripe/get-exp-month card)
     :exp-year       (stripe/get-exp-year card)}))


(defn s-payment-method-sepa->map
  [s-payment-method]
  (let [sepa-debit (stripe/get-sepa-debit s-payment-method)]
    {:payment-method (stripe/get-id s-payment-method)
     :last4          (stripe/get-last4 sepa-debit)}))


(defn s-setup-intent->map
  [s-setup-intent]
  {:client-secret (stripe/get-client-secret s-setup-intent)})


(defn s-invoice-line-item->map
  [s-invoice-line-item]
  {:amount      (stripe/price->unit-float (stripe/get-amount s-invoice-line-item))
   :currency    (stripe/get-currency s-invoice-line-item)
   :description (stripe/get-description s-invoice-line-item)
   :period      (let [p (stripe/get-period s-invoice-line-item)]
                  {:start (some-> (stripe/get-start p)
                                  time/unix-timestamp->str)

                   :end   (some-> (stripe/get-end p)
                                  time/unix-timestamp->str)})
   :quantity    (stripe/get-quantity s-invoice-line-item)})

(defn s-invoice->map
  [s-invoice extend]
  (cond-> {:id          (stripe/get-id s-invoice)
           :number      (stripe/get-number s-invoice)
           :created     (some-> (stripe/get-created s-invoice)
                                time/unix-timestamp->str)
           :currency    (stripe/get-currency s-invoice)
           :due-date    (stripe/get-due-date s-invoice)
           :invoice-pdf (stripe/get-invoice-pdf s-invoice)
           :paid        (stripe/get-paid s-invoice)
           :status      (stripe/get-status s-invoice)
           :total       (stripe/price->unit-float (stripe/get-total s-invoice))}
          extend (assoc :lines (->> s-invoice
                                    stripe/get-lines
                                    stripe/collection-iterator
                                    (map s-invoice-line-item->map)))))


(defn throw-plan-invalid
  [{:keys [plan-id plan-item-ids] :as body} catalogue]
  (when plan-id
    (let [plan (some->> catalogue
                        :plans
                        (some #(when (= (:plan-id %) plan-id) %)))]
      (if plan
        (let [{:keys [required-items optional-items]} plan
              required-items-set      (set required-items)
              all-defined-items       (set/union required-items-set (set optional-items))
              plan-item-ids-set       (set plan-item-ids)
              contain-extra-items?    (not (set/superset? all-defined-items plan-item-ids-set))
              missing-required-items? (not-every? plan-item-ids-set required-items-set)]
          (when (or contain-extra-items? missing-required-items?)
            (logu/log-and-throw-400 (format "Plan-item-ids not valid for plan %s!" plan-id))))
        (logu/log-and-throw-400 (format "Plan-id %s not found!" plan-id))))))


(defn throw-customer-exist
  [id]
  (let [customer-id (try
                      (-> id
                          crud/retrieve-by-id-as-admin
                          :customer-id)
                      (catch Exception _))]
    (when customer-id
      (logu/log-and-throw-400 "Customer exist already!"))))


(defn throw-admin-can-not-be-customer
  [request]
  (when
    (and (nil? (get-in request [:body :parent]))
         (-> request
             (auth/current-authentication)
             (acl-resource/is-admin?)))
    (logu/log-and-throw-400 "Admin can't create customer!")))


(defn throw-invalid-body-fn
  [spec]
  (let [ok?     (partial s/valid? spec)
        explain (partial expound/expound-str spec)]
    (fn [customer]
      (when-not (ok? customer)
        (logu/log-and-throw-400
          (str "resource does not satisfy defined schema:\n" (explain customer)))))))


(defn create-subscription
  [customer-id {:keys [plan-id plan-item-ids payment-method] :as body}]
  (let [catalogue (crud/retrieve-by-id-as-admin pricing/resource-id)]
    (throw-plan-invalid body catalogue)
    (-> (cond-> {"customer"        customer-id
                 "items"           (map (fn [plan-id] {"plan" plan-id})
                                        (cons plan-id plan-item-ids))
                 "trial_from_plan" true}
                payment-method (assoc :default_payment_method payment-method))
        stripe/create-subscription
        s-subscription->map)))


(defn create-customer
  [{:keys [fullname subscription address coupon] pm-id :payment-method} user-id]
  (let [{:keys [street-address city postal-code country]} address
        email       (try (some-> user-id
                                 crud/retrieve-by-id-as-admin
                                 :email
                                 crud/retrieve-by-id-as-admin
                                 :address)
                         (catch Exception _))
        s-customer  (stripe/create-customer
                      (cond-> {"name"    fullname
                               "address" {"line1"       street-address
                                          "city"        city
                                          "postal_code" postal-code
                                          "country"     country}}
                              email (assoc "email" email)
                              pm-id (assoc "payment_method" pm-id
                                           "invoice_settings" {"default_payment_method" pm-id})
                              coupon (assoc "coupon" coupon)))
        customer-id (stripe/get-id s-customer)]
    (when subscription
      (create-subscription subscription customer-id))
    customer-id))


(defn create-setup-intent
  [customer-id]
  (-> {"customer" customer-id}
      stripe/create-setup-intent
      s-setup-intent->map))


(defn get-upcoming-invoice
  [customer-id]
  (some-> {"customer" customer-id}
          stripe/get-upcoming-invoice
          (s-invoice->map true)))


(defn list-invoices
  [customer-id]
  (let [paid (->> {"customer" customer-id
                   "status"   "paid"}
                  stripe/list-invoices
                  stripe/get-data
                  (map #(s-invoice->map % false)))
        open (->> {"customer" customer-id
                   "status"   "open"}
                  stripe/list-invoices
                  stripe/get-data
                  (map #(s-invoice->map % false)))]
    (concat open paid)))


(defn valid-subscription
  [subscription]
  ;; subscription not in incomplete_expired or canceled status
  (when (#{"active" "incomplete" "trialing" "past_due" "unpaid"} (stripe/get-status subscription))
    subscription))


(defn get-current-subscription
  [s-customer]
  (->> s-customer
       (stripe/get-customer-subscriptions)
       (stripe/collection-iterator)
       (some valid-subscription)))


(defn get-default-payment-method
  [s-customer]
  (-> s-customer
      stripe/get-invoice-settings
      stripe/get-default-payment-method))


(defn list-payment-methods
  [s-customer]
  (let [id            (stripe/get-id s-customer)
        cards         (->> {"customer" id
                            "type"     "card"}
                           (stripe/list-payment-methods)
                           (stripe/collection-iterator)
                           (map s-payment-method-card->map))
        bank-accounts (->> {"customer" id
                            "type"     "sepa_debit"}
                           (stripe/list-payment-methods)
                           (stripe/collection-iterator)
                           (map s-payment-method-sepa->map))]
    {:cards                  cards
     :bank-accounts          bank-accounts
     :default-payment-method (get-default-payment-method s-customer)}))


(defn can-do-action?
  [resource request action]
  (let [subscription (:subscription resource)
        can-manage?  (a/can-manage? resource request)]
    (a/can-manage? resource request)
    (condp = action
      create-subscription-action (and can-manage? (nil? subscription))
      create-setup-intent-action can-manage?
      detach-payment-method-action can-manage?
      set-default-payment-method-action can-manage?
      :else false)))


(defn throw-can-not-manage
  [{:keys [id] :as resource} request action]
  (if (a/can-manage? resource request)
    resource
    (throw (r/ex-response (format "action not available for %s!" action id) 409 id))))


(defn throw-plan-id-mandatory
  [{:keys [id] :as resource} {{:keys [plan-id] :as body} :body :as request}]
  (if plan-id
    resource
    (throw (r/ex-response
             (format "plan-id is mandatory for %s on %s!" create-subscription-action id) 409 id))))


(defn throw-subscription-already-exist
  [{:keys [id customer-id] :as resource} request]
  (let [s-customer (stripe/retrieve-customer customer-id)]
    (if (get-current-subscription s-customer)
      (throw (r/ex-response
               (format "subscription already created!" create-subscription-action id) 409 id))
      resource)))
