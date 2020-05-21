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
    [sixsq.nuvla.server.resources.customer.utils :as utils]
    [sixsq.nuvla.server.resources.pricing.stripe :as s]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

;;
;; validate customer
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

(defn request->resource-id
  [request]
  (-> request
      auth/current-user-id
      user-id->resource-id))

;; resource identifier a UUID generated from the email address
(defmethod crud/new-identifier resource-type
  [resource resource-name]
  (assoc resource :id (-> resource :parent user-id->resource-id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [plan-id] :as body} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (a/throw-cannot-add collection-acl request)
  (utils/throw-customer-exist (request->resource-id request))
  (utils/throw-admin-can-not-be-customer request)
  (let [s-customer (utils/create-customer request)]
    (when plan-id
      (utils/create-subscription request s-customer))
    (-> request
        (assoc :body {:parent      (auth/current-user-id request)
                      :customer-id (s/get-id s-customer)})
        add-impl)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defn load-dynamic-attributes
  [{:keys [customer-id] :as resource} request]
  (let [s-customer      (s/retrieve-customer customer-id)
        s-subscription  (utils/get-current-subscription s-customer)
        subscription    (some-> s-subscription utils/s-subscription->map)
        payment-methods (utils/list-payment-methods s-customer)
        default-pm      (utils/get-default-payment-method s-customer)] ;; re set-ops after resource is complete
    (-> resource
        (assoc :subscription subscription
               :payment-methods payment-methods
               :default-payment-method default-pm)
        (crud/set-operations request))))


(defmethod crud/retrieve resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (-> request
      retrieve-impl
      (update :body load-dynamic-attributes request)))


(defn add-session-filter
  [request]
  (->> request
       auth/current-user-id
       user-id->resource-id
       (format "id='%s'")
       (parser/parse-cimi-filter)
       (assoc-in request [:cimi-params :filter])))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (query-impl request))


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [create-subscription-op   (u/action-map id utils/create-subscription-action)
        create-setup-intent-op   (u/action-map id utils/create-setup-intent-action)
        detach-payment-method-op (u/action-map id utils/detach-payment-method-action)]
    (cond-> (crud/set-standard-operations resource request)

            (utils/can-do-action? resource request utils/create-subscription-action)
            (update :operations conj create-subscription-op)

            (utils/can-do-action? resource request utils/create-setup-intent-action)
            (update :operations conj create-setup-intent-op)

            (utils/can-do-action? resource request utils/detach-payment-method-action)
            (update :operations conj detach-payment-method-op)

            )))


(defmethod crud/do-action [resource-type utils/create-subscription-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [customer-id] :as resource} (-> request
                                               (request->resource-id)
                                               (crud/retrieve-by-id-as-admin)
                                               (load-dynamic-attributes request)
                                               (utils/throw-can-not-do-action
                                                 request
                                                 utils/create-subscription-action)
                                               (utils/throw-plan-id-mandatory request))]
    (try
      (some->> customer-id
               s/retrieve-customer
               (utils/create-subscription request)
               utils/s-subscription->map
               r/json-response)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/create-setup-intent-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [customer-id] :as resource} (-> request
                                               (request->resource-id)
                                               (crud/retrieve-by-id-as-admin)
                                               (utils/throw-can-not-do-action
                                                 request
                                                 utils/create-setup-intent-action))]
    (try
      (some->> customer-id
               s/retrieve-customer
               (utils/create-setup-intent request)
               utils/s-setup-intent->map
               r/json-response)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/detach-payment-method-action]
  [{{:keys [payment-method]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id] :as resource} (-> request
                                      (request->resource-id)
                                      (crud/retrieve-by-id-as-admin)
                                      (utils/throw-can-not-do-action
                                        request
                                        utils/detach-payment-method-action))]
    (try
      (some-> payment-method
              s/retrieve-payment-method
              s/detach-payment-method)
      (r/map-response (format "%s successfully detached" payment-method) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


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
;;

;; ==== Move add to user operation create-customer
;; when stripe configured, user is signed-up but not customer. Create customer op available (payment-method optional)
;; when new user and stripe configured create user and customer. (payment-method optional)
;; delete user what about customer and subscription. Delete user also used in post-add exception