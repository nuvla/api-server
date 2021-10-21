(ns sixsq.nuvla.server.resources.customer
  "
Customer mapping to external banking system."
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.customer.utils :as utils]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.customer :as customer]
    [sixsq.nuvla.server.resources.spec.customer-related :as customer-related]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

;;
;; validate customer
;;

(def validate-fn (u/create-spec-validation-fn ::customer/schema))


(def validate-customer-body (utils/throw-invalid-body-fn ::customer-related/customer))


(defmethod crud/validate resource-type
  [resource]
  resource)

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [{:keys [parent] :as resource} _request]
  (assoc resource :acl {:owners   ["group/nuvla-admin"]
                        :view-acl [parent]
                        :manage   [parent]}))

;;
;; "Implementations" of multimethod declared in crud namespace
;;

(defn active-claim->resource-id
  [active-claim]
  (->> active-claim
       u/parse-id
       (str/join "-")
       (str resource-type "/")))

(defn request->resource-id
  [{{uuid :uuid} :params :as _request}]
  (str resource-type "/" uuid))


;; resource identifier a UUID generated from the user-id
(defmethod crud/new-identifier resource-type
  [resource _resource-name]
  (assoc resource :id (-> resource :parent active-claim->resource-id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [email] :as body} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (utils/throw-admin-cannot-be-customer request)
  (config-nuvla/throw-stripe-not-configured)
  (let [auth-info    (auth/current-authentication request)
        active-claim (or
                       (when (a/is-admin? auth-info) (:parent body))
                       (auth/current-active-claim request))]
    (utils/throw-email-mandatory-for-group active-claim email)
    (utils/throw-customer-exist (active-claim->resource-id active-claim))
    (validate-customer-body (dissoc body :parent))
    (let [[customer-id subscription-id] (utils/create-customer body active-claim)]
      (-> request
          (assoc :body (cond-> {:parent      active-claim
                                :customer-id customer-id}
                               subscription-id (assoc :subscription-id subscription-id)))
          add-impl))))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (retrieve-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (query-impl request))

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [can-manage?                   (a/can-manage? resource request)
        customer-info-op              (u/action-map id utils/customer-info-action)
        update-customer-op            (u/action-map id utils/update-customer-action)
        get-subscription-op           (u/action-map id utils/get-subscription-action)
        create-subscription-op        (u/action-map id utils/create-subscription-action)
        create-setup-intent-op        (u/action-map id utils/create-setup-intent-action)
        list-payment-methods-op       (u/action-map id utils/list-payment-methods-action)
        detach-payment-method-op      (u/action-map id utils/detach-payment-method-action)
        set-default-payment-method-op (u/action-map id utils/set-default-payment-method-action)
        upcoming-invoice-op           (u/action-map id utils/upcoming-invoice-action)
        list-invoices-op              (u/action-map id utils/list-invoices-action)
        add-coupon-op                 (u/action-map id utils/add-coupon-action)
        delete-coupon-op              (u/action-map id utils/delete-coupon-action)]
    (cond-> (crud/set-standard-operations resource request)

            can-manage? (update :operations concat [customer-info-op
                                                    update-customer-op
                                                    get-subscription-op
                                                    create-subscription-op
                                                    create-setup-intent-op
                                                    list-payment-methods-op
                                                    set-default-payment-method-op
                                                    detach-payment-method-op
                                                    upcoming-invoice-op
                                                    list-invoices-op
                                                    add-coupon-op
                                                    delete-coupon-op]))))


(defmethod crud/do-action [resource-type utils/get-subscription-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (r/json-response
      (or (some-> request
                  (request->resource-id)
                  (crud/retrieve-by-id-as-admin)
                  (a/throw-cannot-manage request)
                  :subscription-id
                  pricing-impl/retrieve-subscription
                  pricing-impl/subscription->map)
          {}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/customer-info-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (-> request
        (request->resource-id)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        :customer-id
        pricing-impl/retrieve-customer
        pricing-impl/customer->map
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/update-customer-action]
  [{{:keys [fullname address] :as body} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (validate-customer-body body)
  (try
    (let [{:keys [street-address city postal-code country]} address
          {:keys [id customer-id] :as _resource} (-> request
                                                     (request->resource-id)
                                                     (crud/retrieve-by-id-as-admin)
                                                     (a/throw-cannot-manage request))]
      (try
        (-> customer-id
            pricing-impl/retrieve-customer
            (pricing-impl/update-customer {"name"    fullname
                                           "address" {"line1"       street-address
                                                      "city"        city
                                                      "postal_code" postal-code
                                                      "country"     country}}))
        (r/map-response "successfully updated" 200 id)
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn update-customer
  [customer-id k v]
  (try
    (-> (crud/retrieve-by-id-as-admin customer-id)
        (u/update-timestamps)
        (assoc k v)
        (db/edit {:nuvla/authn auth/internal-identity}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/create-subscription-action]
  [{body :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (let [customer-id  (-> request
                           (request->resource-id)
                           (crud/retrieve-by-id-as-admin)
                           (a/throw-cannot-manage request)
                           (utils/throw-plan-id-mandatory request)
                           (utils/throw-subscription-already-exist request)
                           :customer-id)
          subscription (utils/create-subscription customer-id body false)]
      (update-customer customer-id :subscription-id (:id subscription))
      (r/json-response subscription))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/create-setup-intent-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (-> request
        (request->resource-id)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        :customer-id
        utils/create-setup-intent
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/list-payment-methods-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (-> request
        (request->resource-id)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        :customer-id
        pricing-impl/retrieve-customer
        (pricing-impl/list-payment-methods)
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/detach-payment-method-action]
  [{{:keys [payment-method]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id] :as _resource} (-> request
                                       (request->resource-id)
                                       (crud/retrieve-by-id-as-admin)
                                       (a/throw-cannot-manage request))]
    (try
      (some-> payment-method
              pricing-impl/retrieve-payment-method
              pricing-impl/detach-payment-method)
      (r/map-response (format "%s successfully detached" payment-method) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/set-default-payment-method-action]
  [{{:keys [payment-method]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id customer-id] :as _resource} (-> request
                                                   (request->resource-id)
                                                   (crud/retrieve-by-id-as-admin)
                                                   (a/throw-cannot-manage request))]
    (try
      (-> customer-id
          pricing-impl/retrieve-customer
          (pricing-impl/update-customer {"invoice_settings" {"default_payment_method" payment-method}}))
      (r/map-response (format "%s successfully set as default" payment-method) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/add-coupon-action]
  [{{:keys [coupon]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id customer-id] :as _resource} (-> request
                                                   (request->resource-id)
                                                   (crud/retrieve-by-id-as-admin)
                                                   (a/throw-cannot-manage request))]
    (try
      (-> customer-id
          pricing-impl/retrieve-customer
          (pricing-impl/update-customer {"coupon" coupon}))
      (r/map-response (format "%s successfully added coupon" coupon) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/delete-coupon-action]
  [{{:keys [coupon]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id customer-id] :as _resource} (-> request
                                                   (request->resource-id)
                                                   (crud/retrieve-by-id-as-admin)
                                                   (a/throw-cannot-manage request))]
    (try
      (-> customer-id
          pricing-impl/retrieve-customer
          pricing-impl/delete-discount-customer)
      (r/map-response (format "%s successfully deleted coupon" coupon) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/upcoming-invoice-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (r/json-response
      (or (-> request
              (request->resource-id)
              (crud/retrieve-by-id-as-admin)
              (a/throw-cannot-manage request)
              :subscription-id
              (pricing-impl/get-upcoming-invoice))
          {}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/list-invoices-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (-> request
        (request->resource-id)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        :subscription-id
        (utils/list-invoices)
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


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


;; ==== Move add to user operation create-customer
;; when stripe configured, user is signed-up but not customer. Create customer op available (payment-method optional)
;; when new user and stripe configured create user and customer. (payment-method optional)
;; delete user what about customer and subscription. Delete user also used in post-add exception