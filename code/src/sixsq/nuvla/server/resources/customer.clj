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
    [sixsq.nuvla.server.resources.pricing.stripe :as s]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.pricing :as pricing]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [clojure.set :as set]
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


(defn throw-plan-invalid
  [{{:keys [plan-id plan-item-ids] :as body} :body :as request}]
  (when plan-id
    (let [catalogue (crud/retrieve-by-id-as-admin pricing/resource-id)
          plan      (some->> catalogue
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
  [{{:keys [plan-id plan-item-ids] :as body} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (throw-customer-exist request)
  (throw-admin-can-not-be-customer request)
  (throw-plan-invalid request)

  (let [s-customer (create-customer request)]
    (when plan-id
      (s/create-subscription {"customer" (s/get-id s-customer)
                              "items"    (map (fn [plan-id] {"plan" plan-id})
                                              (cons plan-id plan-item-ids))}))
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


;(defmethod crud/set-operations resource-type
;  [{:keys [id] :as resource} request]
;  (let [create-subscription-op (u/action-map id :create-subscription)
;        stop-op                (u/action-map id :stop)
;        update-op              (u/action-map id :update)
;        create-log-op          (u/action-map id :create-log)
;        clone-op               (u/action-map id :clone)
;        fetch-module-op        (u/action-map id :fetch-module)
;        can-manage?            (a/can-manage? resource request)
;        can-clone?             (a/can-view-data? resource request)]
;    (cond-> (crud/set-standard-operations resource request)
;
;            (and can-manage? (dep-utils/can-start? resource)) (update :operations conj start-op)
;
;            (and can-manage? (dep-utils/can-stop? resource))
;            (update :operations conj stop-op)
;
;            (and can-manage? (dep-utils/can-update? resource)) (update :operations conj update-op)
;
;            (and can-manage? (dep-utils/can-create-log? resource))
;            (update :operations conj create-log-op)
;
;            (and can-manage? can-clone?)
;            (update :operations conj clone-op)
;
;            (and can-manage? (dep-utils/can-fetch-module? resource))
;            (update :operations conj fetch-module-op)
;
;            (not (dep-utils/can-delete? resource))
;            (update :operations dep-utils/remove-delete))))


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