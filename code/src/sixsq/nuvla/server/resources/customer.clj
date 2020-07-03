(ns sixsq.nuvla.server.resources.customer
  "
Customer mapping to external banking system."
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.customer.utils :as utils]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]
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
  [{:keys [parent] :as resource} request]
  (assoc resource :acl {:owners   ["group/nuvla-admin"]
                        :view-acl [parent]
                        :manage   [parent]}))

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
  [{{uuid :uuid} :params :as request}]
  (str resource-type "/" uuid))


;; resource identifier a UUID generated from the user-id
(defmethod crud/new-identifier resource-type
  [resource resource-name]
  (assoc resource :id (-> resource :parent user-id->resource-id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{body :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (utils/throw-admin-can-not-be-customer request)
  (config-nuvla/throw-stripe-not-configured)
  (let [auth-info (auth/current-authentication request)
        user-id   (or
                    (when (acl-resource/is-admin? auth-info) (:parent body))
                    (auth/current-user-id request))]
    (utils/throw-customer-exist (user-id->resource-id user-id))
    (validate-customer-body (dissoc body :parent))
    (-> request
        (assoc :body {:parent      user-id
                      :customer-id (utils/create-customer body user-id)})
        add-impl)))


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

(defn customer-has-active-subscription?
  [user-id]
  (boolean
    (try
      (some-> resource-type
              (crud/query-as-admin {:cimi-params {:filter (parser/parse-cimi-filter
                                                            (format "parent='%s'" user-id))}})
              second
              first
              :customer-id
              stripe/retrieve-customer
              utils/get-current-subscription
              utils/s-subscription->map
              :status
              (#{"active" "trialing"}))
      (catch Exception _))))


(defn throw-user-hasnt-active-subscription
  [request]
  (let [user-id (auth/current-user-id request)]
    (when (and config-nuvla/*stripe-api-key*
               (not (customer-has-active-subscription? user-id)))
      (throw (r/ex-response "An active subscription is required!" 402)))))


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
                  :customer-id
                  stripe/retrieve-customer
                  utils/get-current-subscription
                  utils/s-subscription->map)
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
        stripe/retrieve-customer
        utils/s-customer->customer-map
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/update-customer-action]
  [{{:keys [fullname address] :as body} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (validate-customer-body body)
  (try
    (let [{:keys [street-address city postal-code country]} address
          {:keys [id customer-id] :as resource} (-> request
                                                    (request->resource-id)
                                                    (crud/retrieve-by-id-as-admin)
                                                    (a/throw-cannot-manage request))]
      (try
        (-> customer-id
            stripe/retrieve-customer
            (stripe/update-customer {"name"    fullname
                                     "address" {"line1"       street-address
                                                "city"        city
                                                "postal_code" postal-code
                                                "country"     country}}))
        (r/map-response (format "successfully updated") 200 id)
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/create-subscription-action]
  [{body :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (-> request
        (request->resource-id)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        (utils/throw-plan-id-mandatory request)
        (utils/throw-subscription-already-exist request)
        :customer-id
        (utils/create-subscription body false)
        r/json-response)
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
        stripe/retrieve-customer
        (utils/list-payment-methods)
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/detach-payment-method-action]
  [{{:keys [payment-method]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id] :as resource} (-> request
                                      (request->resource-id)
                                      (crud/retrieve-by-id-as-admin)
                                      (a/throw-cannot-manage request))]
    (try
      (some-> payment-method
              stripe/retrieve-payment-method
              stripe/detach-payment-method)
      (r/map-response (format "%s successfully detached" payment-method) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/set-default-payment-method-action]
  [{{:keys [payment-method]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id customer-id] :as resource} (-> request
                                                  (request->resource-id)
                                                  (crud/retrieve-by-id-as-admin)
                                                  (a/throw-cannot-manage request))]
    (try
      (-> customer-id
          stripe/retrieve-customer
          (stripe/update-customer {"invoice_settings" {"default_payment_method" payment-method}}))
      (r/map-response (format "%s successfully set as default" payment-method) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/add-coupon-action]
  [{{:keys [coupon]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id customer-id] :as resource} (-> request
                                                  (request->resource-id)
                                                  (crud/retrieve-by-id-as-admin)
                                                  (a/throw-cannot-manage request))]
    (try
      (-> customer-id
          stripe/retrieve-customer
          (stripe/update-customer {"coupon" coupon}))
      (r/map-response (format "%s successfully added coupon" coupon) 200 id)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type utils/delete-coupon-action]
  [{{:keys [coupon]} :body :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (let [{:keys [id customer-id] :as resource} (-> request
                                                  (request->resource-id)
                                                  (crud/retrieve-by-id-as-admin)
                                                  (a/throw-cannot-manage request))]
    (try
      (-> customer-id
          stripe/retrieve-customer
          stripe/delete-discount-customer)
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
              :customer-id
              (utils/get-upcoming-invoice))
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
        :customer-id
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