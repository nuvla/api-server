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
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [clojure.tools.logging :as log]))


(def ^:const create-subscription-action "create-subscription")
(def ^:const create-setup-intent-action "create-setup-intent")

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
  [{{:keys [plan-id plan-item-ids] :as body} :body :as request} s-customer]
  (let [catalogue (crud/retrieve-by-id-as-admin pricing/resource-id)]
    (throw-plan-invalid request catalogue)
    (s/create-subscription {"customer"        (s/get-id s-customer)
                            "items"           (map (fn [plan-id] {"plan" plan-id})
                                                   (cons plan-id plan-item-ids))
                            "trial_from_plan" true})))


(defn create-setup-intent
  [request s-customer]
  (s/create-setup-intent {"customer"        (s/get-id s-customer)}))


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


(defn can-do-action?
  [resource request action]
  (let [subscription (:subscription resource)
        can-manage?  (a/can-manage? resource request)]
    (condp = action
      create-subscription-action (and can-manage? (nil? subscription))
      create-setup-intent-action can-manage?
      :else false)))


(defn throw-can-not-do-action
  [{:keys [id] :as resource} request action]
  (if (can-do-action? resource request action)
    resource
    (throw (r/ex-response (format "action not available for %s!" action id) 409 id))))


(defn throw-plan-id-mandatory
  [{:keys [id] :as resource} {{:keys [plan-id] :as body} :body :as request}]
  (if plan-id
    resource
    (throw (r/ex-response
             (format "plan-id is mandatory for %s on %s!" create-subscription-action id) 409 id))))

