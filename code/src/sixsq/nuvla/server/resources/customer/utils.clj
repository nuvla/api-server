(ns sixsq.nuvla.server.resources.customer.utils
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.pricing.stripe :as s]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.server.resources.pricing :as pricing]
    [clojure.set :as set]
    [sixsq.nuvla.server.util.response :as r]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.util.time :as time]))


(def ^:const get-subscription-action "get-subscription")
(def ^:const create-subscription-action "create-subscription")
(def ^:const create-setup-intent-action "create-setup-intent")
(def ^:const list-payment-methods-action "list-payment-methods")
(def ^:const set-default-payment-method-action "set-default-payment-method")
(def ^:const detach-payment-method-action "detach-payment-method")
(def ^:const upcoming-invoice-action "upcoming-invoice")
(def ^:const list-invoices-action "list-invoices")

(defn s-subscription->map
  [s-subscription]
  (cond-> {:status               (s/get-status s-subscription)
           :start-date           (some-> (s/get-start-date s-subscription)
                                         time/unix-timestamp->str)
           :current-period-start (some-> (s/get-current-period-start s-subscription)
                                         time/unix-timestamp->str)
           :current-period-end   (some-> (s/get-current-period-end s-subscription)
                                         time/unix-timestamp->str)
           :trial-start          (some-> (s/get-trial-start s-subscription)
                                         time/unix-timestamp->str)
           :trial-end            (some-> (s/get-trial-end s-subscription)
                                         time/unix-timestamp->str)}))


(defn s-payment-method-card->map
  [s-payment-method]
  (let [card (s/get-card s-payment-method)]
    {:payment-method (s/get-id s-payment-method)
     :brand          (s/get-brand card)
     :last4          (s/get-last4 card)
     :exp-month      (s/get-exp-month card)
     :exp-year       (s/get-exp-year card)}))


(defn s-payment-method-sepa->map
  [s-payment-method]
  (let [sepa-debit (s/get-sepa-debit s-payment-method)]
    {:payment-method (s/get-id s-payment-method)
     :last4          (s/get-last4 sepa-debit)}))


(defn s-setup-intent->map
  [s-setup-intent]
  {:client-secret (s/get-client-secret s-setup-intent)})


(defn s-invoice-line-item->map
  [s-invoice-line-item]
  {:amount      (s/price->unit-float (s/get-amount s-invoice-line-item))
   :currency    (s/get-currency s-invoice-line-item)
   :description (s/get-description s-invoice-line-item)
   :period      (let [p (s/get-period s-invoice-line-item)]
                  {:start (some-> (s/get-start p)
                                  time/unix-timestamp->str)

                   :end   (some-> (s/get-end p)
                                  time/unix-timestamp->str)})
   :quantity    (s/get-quantity s-invoice-line-item)})

(defn s-invoice->map
  [s-invoice extend]
  (cond-> {:id          (s/get-id s-invoice)
           :number      (s/get-number s-invoice)
           :created     (some-> (s/get-created s-invoice)
                                time/unix-timestamp->str)
           :currency    (s/get-currency s-invoice)
           :due-date    (s/get-due-date s-invoice)
           :invoice-pdf (s/get-invoice-pdf s-invoice)
           :paid        (s/get-paid s-invoice)
           :status      (s/get-status s-invoice)
           :total       (s/price->unit-float (s/get-total s-invoice))}
          extend (assoc :lines (->> s-invoice
                                    s/get-lines
                                    s/collection-iterator
                                    (map s-invoice-line-item->map)))))


(defn create-customer
  [request]
  (let [user-id (auth/current-user-id request)
        email   (try (some-> user-id
                             crud/retrieve-by-id-as-admin
                             :email
                             crud/retrieve-by-id-as-admin
                             :address)
                     (catch Exception _))
        pm-id   (get-in request [:body :payment-method-id])]
    (s/create-customer
      (cond-> {}
              email (assoc "email" email)
              pm-id (assoc "payment_method" pm-id
                           "invoice_settings" {"default_payment_method" pm-id})))))


(defn throw-plan-invalid
  [{{:keys [plan-id plan-item-ids] :as body} :body :as request} catalogue]
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
  (when (-> request
            (auth/current-authentication)
            (acl-resource/is-admin?))
    (logu/log-and-throw-400 "Admin can't create customer!")))


(defn create-subscription
  [{{:keys [plan-id plan-item-ids] :as body} :body :as request} customer-id]
  (let [catalogue (crud/retrieve-by-id-as-admin pricing/resource-id)]
    (throw-plan-invalid request catalogue)
    (-> {"customer"        customer-id
         "items"           (map (fn [plan-id] {"plan" plan-id})
                                (cons plan-id plan-item-ids))
         "trial_from_plan" true}
        s/create-subscription
        s-subscription->map)))


(defn create-setup-intent
  [customer-id]
  (-> {"customer" customer-id}
      s/create-setup-intent
      s-setup-intent->map))

(defn get-upcoming-invoice
  [customer-id]
  (-> {"customer" customer-id}
      s/get-upcoming-invoice
      (s-invoice->map true)))


(defn list-invoices
  [customer-id]
  (let [paid (->> {"customer" customer-id
                   "status"   "paid"}
                  s/list-invoices
                  s/get-data
                  (map #(s-invoice->map % false)))
        open (->> {"customer" customer-id
                   "status"   "open"}
                  s/list-invoices
                  s/get-data
                  (map #(s-invoice->map % false)))]
    (concat open paid)))


(defn valid-subscription
  [subscription]
  ;; subscription not in incomplete_expired or canceled status
  (when (#{"active" "incomplete" "trialing" "past_due" "unpaid"} (s/get-status subscription))
    subscription))


(defn get-current-subscription
  [s-customer]
  (->> s-customer
       (s/get-customer-subscriptions)
       (s/collection-iterator)
       (some valid-subscription)))


(defn get-default-payment-method
  [s-customer]
  (-> s-customer
      s/get-invoice-settings
      s/get-default-payment-method))


(defn list-payment-methods
  [s-customer]
  (let [id            (s/get-id s-customer)
        cards         (->> {"customer" id
                            "type"     "card"}
                           (s/list-payment-methods)
                           (s/collection-iterator)
                           (map s-payment-method-card->map))
        bank-accounts (->> {"customer" id
                            "type"     "sepa_debit"}
                           (s/list-payment-methods)
                           (s/collection-iterator)
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
  (let [s-customer (s/retrieve-customer customer-id)]
    (if (get-current-subscription s-customer)
      (throw (r/ex-response
               (format "subscription already created!" create-subscription-action id) 409 id))
      resource)))
