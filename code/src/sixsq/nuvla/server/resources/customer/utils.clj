(ns sixsq.nuvla.server.resources.customer.utils
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [expound.alpha :as expound]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.pricing :as pricing]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))

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


(defn throw-plan-invalid
  [{:keys [plan-id plan-item-ids] :as _body} catalogue]
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


(defn throw-email-mandatory-for-group
  [active-claim email]
  (when (and (str/starts-with? active-claim "group/")
             (str/blank? email))
    (logu/log-and-throw-400 "Customer email is mandatory for group!")))


(defn throw-admin-cannot-be-customer
  [request]
  (when
    (and (nil? (get-in request [:body :parent]))
         (-> request
             (auth/current-authentication)
             (a/is-admin?)))
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
  [customer-id {:keys [plan-id plan-item-ids] :as body} trial?]
  (let [catalogue (crud/retrieve-by-id-as-admin pricing/resource-id)]
    (throw-plan-invalid body catalogue)
    (-> {"customer"          customer-id
         "items"             (map (fn [plan-id] {"price" plan-id})
                                  (cons plan-id plan-item-ids))
         "collection_method" "send_invoice"
         "days_until_due"    14}
        (cond-> trial? (assoc "trial_period_days" 14))
        pricing-impl/create-subscription
        pricing-impl/subscription->map)))


(defn create-customer
  [{:keys [fullname subscription address coupon email] pm-id :payment-method} active-claim]
  (let [{:keys [street-address city postal-code country]} address
        email           (or email
                            (when (str/starts-with? active-claim "user/")
                              (try
                                (some-> active-claim
                                        crud/retrieve-by-id-as-admin
                                        :email
                                        crud/retrieve-by-id-as-admin
                                        :address)
                                (catch Exception _))))
        s-customer      (pricing-impl/create-customer
                          (cond-> {"name"    fullname
                                   "address" {"line1"       street-address
                                              "city"        city
                                              "postal_code" postal-code
                                              "country"     country}}
                                  email (assoc "email" email)
                                  pm-id (assoc "payment_method" pm-id
                                               "invoice_settings" {"default_payment_method" pm-id})
                                  coupon (assoc "coupon" coupon)))
        customer-id     (pricing-impl/get-id s-customer)
        subscription-id (when subscription
                          (try
                            (-> customer-id
                                (create-subscription subscription true)
                                :id)
                            (catch Exception e
                              (pricing-impl/delete-customer s-customer)
                              (throw e))))]
    [customer-id subscription-id]))


(defn create-setup-intent
  [customer-id]
  (-> {"customer" customer-id}
      pricing-impl/create-setup-intent
      pricing-impl/setup-intent->map))


(defn list-invoices
  [subscription-id]
  (let [paid (->> {"subscription" subscription-id
                   "status"       "paid"}
                  pricing-impl/list-invoices
                  pricing-impl/get-data
                  (map #(pricing-impl/invoice->map % false)))
        open (->> {"subscription" subscription-id
                   "status"       "open"}
                  pricing-impl/list-invoices
                  pricing-impl/get-data
                  (map #(pricing-impl/invoice->map % false)))]
    (concat open paid)))


(defn throw-plan-id-mandatory
  [{:keys [id] :as resource} {{:keys [plan-id]} :body :as _request}]
  (if plan-id
    resource
    (throw (r/ex-response
             (format "plan-id is mandatory for %s on %s!" create-subscription-action id) 409 id))))


(defn throw-subscription-already-exist
  [{:keys [id subscription-id] :as resource} _request]
  (if subscription-id
    resource
    (throw (r/ex-response
             (format "subscription already created! %s on %s" create-subscription-action id) 409 id))))
