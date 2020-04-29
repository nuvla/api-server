(ns sixsq.nuvla.server.resources.customer
  "
Customer mapping to external banking system."
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.customer :as customer]
    [sixsq.nuvla.server.resources.stripe.utils :as s]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [clojure.tools.logging :as log]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

;;
;; validate deployment
;;

(def validate-fn (u/create-spec-validation-fn ::customer/schema))


(defmethod crud/validate resource-type
  [resource]
  resource)

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (let [user-id (auth/current-user-id request)]
    (assoc resource :acl {:owners   ["group/nuvla-admin"]
                          :view-acl [user-id]
                          :manage   [user-id]})))

;;
;; "Implementations" of multimethod declared in crud namespace
;;

(defn user-id->resource-id
  [user-id]
  (->> user-id
       u/parse-id
       (str/join "-")
       (str resource-type "/")))

;; resource identifier a UUID generated from the email address
(defmethod crud/new-identifier resource-type
  [resource resource-name]
  (assoc resource :id (-> resource :parent user-id->resource-id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


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


(defn throw-customer-exist
  [request]
  (let [id          (-> request
                        auth/current-user-id
                        user-id->resource-id)
        customer-id (try
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


(defmethod crud/add resource-type
  [{{:keys [plan-ids] :as body} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (throw-customer-exist request)
  (throw-admin-can-not-be-customer request)
  (let [s-customer (create-customer request)]
    (when (seq plan-ids)
      (s/create-subscription {"customer" (s/get-id s-customer)
                              "items"    (map (fn [plan-id] {"plan" plan-id}) plan-ids)}))
    (-> request
        (assoc :body {:parent      (auth/current-user-id request)
                      :customer-id (s/get-id s-customer)})
        add-impl)))

(def retrieve-impl (std-crud/retrieve-fn resource-type))

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

(defn is-product-metadata-in-values?
  [product key values]
  (when-let [values-set (some-> values seq set)]
    (-> product
        (s/get-metadata)
        (get key)
        (values-set))))

(def META_KEY_NUVLA "NUVLA")
(def META_NUVLA_PLAN "PLAN")
(def META_NUVLA_PLAN_ITEM "PLAN_ITEM")
(def META_KEY_ORDER "ORDER")
(def META_KEY_REQUIRED_PLAN_ITEM "REQUIRED_PLAN_ITEM")
(def META_KEY_OPTIONAL_PLAN_ITEM "OPTIONAL_PLAN_ITEM")

(defn is-nuvla-product?
  [product]
  (is-product-metadata-in-values?
    product META_KEY_NUVLA #{META_NUVLA_PLAN META_NUVLA_PLAN_ITEM}))

(defn get-nuvla-products
  []
  (->> (s/list-products {"active" true})
       (s/collection-iterator)
       (filter is-nuvla-product?)))

(defn get-nuvla-plan-splited
  []
  (->> (get-nuvla-products)
       (group-by #(get (s/get-metadata %) META_KEY_NUVLA))))

(defn amount->unit-float
  [amount]
  (some-> amount (/ 100) float))

(defn stripe-product-plan->charge
  [stripe-product-plan]
  (let [amount          (amount->unit-float (s/get-amount stripe-product-plan))
        aggregate-usage (s/get-aggregate-usage stripe-product-plan)
        tiers-mode      (s/get-tiers-mode stripe-product-plan)
        tiers           (->> stripe-product-plan
                             (s/get-tiers)
                             (map-indexed
                               (fn [i tier]
                                 {:order  i
                                  :amount (amount->unit-float (s/get-unit-amount tier))
                                  :up-to  (s/get-up-to tier)}))
                             )]
    (cond-> {:currency       (s/get-currency stripe-product-plan)
             :interval       (s/get-interval stripe-product-plan)
             :usage_type     (s/get-usage-type stripe-product-plan)
             :billing_scheme (s/get-billing-scheme stripe-product-plan)}
            amount (assoc :amount amount)
            aggregate-usage (assoc :aggregate-usage aggregate-usage)
            tiers-mode (assoc :tiers-mode tiers-mode)
            tiers (assoc :tiers tiers))))


(defn transform-plan-items
  [plan-item]
  (let [stripe-product-plans (-> (s/list-plans {"active"  true
                                                "product" (s/get-id plan-item)})
                                 s/collection-iterator
                                 seq)
        metadata             (s/get-metadata plan-item)
        required-items       (some-> metadata (get META_KEY_REQUIRED_PLAN_ITEM) (str/split #","))
        optional-items       (some-> metadata (get META_KEY_OPTIONAL_PLAN_ITEM) (str/split #","))
        order                (Integer/parseInt (get metadata META_KEY_ORDER "999"))]
    (map
      (fn [stripe-product-plan]
        (let [id (s/get-id stripe-product-plan)]
          (cond-> {:id     id
                   :name   (s/get-name plan-item)
                   :charge (stripe-product-plan->charge stripe-product-plan)}
                  order (assoc :order order)
                  required-items (assoc :required-items required-items)
                  optional-items (assoc :optional-items optional-items))))
      stripe-product-plans)))

(defn build-nuvla-catalogue
  []
  (let [nuvla-plan-splited     (get-nuvla-plan-splited)
        plans                  (get nuvla-plan-splited META_NUVLA_PLAN [])
        plan-items             (get nuvla-plan-splited META_NUVLA_PLAN_ITEM [])
        tranformed-plans       (->> plans
                                    (map transform-plan-items)
                                    flatten)
        transformed-plan-items (->> plan-items
                                    (map transform-plan-items)
                                    flatten)]
    {:plans      tranformed-plans
     :plan-items transformed-plan-items}))

(defmethod crud/retrieve resource-type
  [request]
  (let [{customer :body :as response} (retrieve-impl request)
        s-customer        (s/retrieve-customer (:customer-id customer))
        s-subscription    (get-current-subscription s-customer)
        subscription-info (when s-subscription
                            {:status               (s/get-status s-subscription)
                             :start-date           (s/get-start-date s-subscription)
                             :current-period-start (s/get-current-period-start s-subscription)
                             :current-period-end   (s/get-current-period-end s-subscription)})]
    (->> subscription-info
         (assoc customer :subscription)
         (assoc response :body))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::customer/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::customer/schema)
  (md/register resource-metadata))

;; =====
;; ADD
;; =====
;; DONE args
;; DONE payment-method-id (optional)
;; TODO selected-plan and optional products (optional)
;; DONE user-id extract email to post to create-customer

;; DONE when payment method set as default

;; TODO when selected-plan create subscription (check selected plan and options validity)

;; DONE save customer id in ES document and id=user-id

;; =====
;; Retrieve
;; =====
;; DONE retrieve customer document from ES
;; DONE stripe retrieve customer
;; get subscription status
;; get subscription name
;; get subscription options (support)
;; -----
;; set operations
;; -----
;; when no subscription (or status subscription not valid) create-subscripiton
;; when subscription get-subscription
;; when subscription update-subscription
;; when subscription cancel-subscription
;; when subscription upgrade-subscription
;; when subscription downgrade-subscription (> Basic)
;; attach payment method
;; detach payment method
;; set payment-method as default

;; =====
;; Query
;; =====
;; search for id=user-id
;;
;; return 1 or 0 document