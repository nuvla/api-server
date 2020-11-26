(ns sixsq.nuvla.server.resources.deployment
  "
These resources represent the deployment of a component or application within
a container orchestration engine.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.customer :as customer]
    [sixsq.nuvla.server.resources.customer.utils :as customer-utils]
    [sixsq.nuvla.server.resources.deployment.utils :as utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


(def actions [{:name           "start"
               :uri            "start"
               :description    "starts the deployment"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "stop"
               :uri            "stop"
               :description    "stops the deployment"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name             "create-log"
               :uri              "create-log"
               :description      (str "creates a new deployment-log resource "
                                      "to collect logging information")
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"

               :input-parameters [{:name "service"}

                                  {:name "since"}

                                  {:name        "lines"
                                   :value-scope {:minimum 1
                                                 :default 200}}]}

              {:name           "update"
               :uri            "update"
               :description    "update the deployment image"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "clone"
               :uri            "clone"
               :description    "clone the deployment"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}


              {:name           "fetch-module"
               :uri            "fetch-module"
               :description    "fetch the deployment module href and merge it"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "check-dct"
               :uri            "check-dct"
               :description    "check if images are trusted"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])


;;
;; validate deployment
;;

(def validate-fn (u/create-spec-validation-fn ::deployment-spec/deployment))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;


(defn create-subscription
  [active-claim {:keys [account-id price-id] :as price} coupon]
  (stripe/create-subscription
    {"customer"                (some-> active-claim
                                       customer/active-claim->customer
                                       :customer-id)
     "items"                   [{"price" price-id}]
     "application_fee_percent" 20
     "trial_period_days"       1
     "coupon"                  coupon
     "transfer_data"           {"destination" account-id}}))


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn create-deployment
  [{:keys [base-uri] {:keys [owner]} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (customer/throw-user-hasnt-active-subscription request)
  (let [authn-info      (auth/current-authentication request)
        is-admin?       (acl-resource/is-admin? authn-info)
        dep-owner       (if is-admin? (or owner "group/nuvla-admin")
                                      (auth/current-active-claim request))
        deployment      (-> request
                            (utils/create-deployment)
                            (assoc :resource-type resource-type
                                   :state "CREATED"
                                   :api-endpoint (str/replace-first base-uri #"/api/" "")
                                   :owner dep-owner)
                            (utils/throw-price-need-payment-method request))
        ;; FIXME: Correct the value passed to the python API.

        create-response (add-impl (assoc request :body deployment))

        deployment-id   (get-in create-response [:body :resource-id])

        msg             (get-in create-response [:body :message])]

    (event-utils/create-event deployment-id msg (a/default-acl authn-info))

    (utils/assoc-api-credentials deployment-id authn-info)

    create-response))

(defmethod crud/add resource-type
  [request]
  (create-deployment request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{:keys [acl parent state module]} :body {uuid :uuid} :params :as request}]
  (let [authn-info (auth/current-authentication request)
        current    (db/retrieve (str resource-type "/" uuid) request)
        fixed-attr (select-keys (:module current) [:href :price :license])
        is-user?   (not (acl-resource/is-admin? authn-info))
        new-acl    (when (and is-user? acl)
                     (if-let [current-owner (:owner current)]
                       (assoc acl :owners (-> acl :owners set (conj current-owner) vec))
                       acl))
        infra-id   (some-> parent (crud/retrieve-by-id {:nuvla/authn authn-info}) :parent)
        stopped?   (and
                     (= (:state current) "STOPPING")
                     (= state "STOPPED"))
        subs-id    (when (and config-nuvla/*stripe-api-key* stopped?)
                     (:subscription-id current))
        response   (edit-impl
                     (cond-> request
                             is-user? (update :body dissoc :owner :infrastructure-service
                                              :subscription-id :state)
                             (and is-user? module) (update-in [:body :module] merge fixed-attr)
                             is-user? (update-in [:cimi-params :select] disj
                                                 "owner" "infrastructure-service" "module/price"
                                                 "module/license" "subscription-id")
                             new-acl (assoc-in [:body :acl] new-acl)
                             infra-id (assoc-in [:body :infrastructure-service] infra-id)))]
    (some-> subs-id stripe/retrieve-subscription (stripe/cancel-subscription {"invoice_now" true}))
    response))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [deployment-id   (str resource-type "/" uuid)
          delete-response (-> deployment-id
                              (db/retrieve request)
                              (utils/throw-can-not-do-action utils/can-delete? "delete")
                              (a/throw-cannot-delete request)
                              (db/delete request))]
      (utils/delete-all-child-resources deployment-id)
      delete-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [start-op            (u/action-map id :start)
        stop-op             (u/action-map id :stop)
        update-op           (u/action-map id :update)
        create-log-op       (u/action-map id :create-log)
        clone-op            (u/action-map id :clone)
        fetch-module-op     (u/action-map id :fetch-module)
        check-dct-op        (u/action-map id :check-dct)
        upcoming-invoice-op (u/action-map id :upcoming-invoice)
        can-manage?         (a/can-manage? resource request)
        can-clone?          (a/can-view-data? resource request)]
    (cond-> (crud/set-standard-operations resource request)

            (and can-manage? (utils/can-start? resource)) (update :operations conj start-op)

            (and can-manage? (utils/can-stop? resource))
            (update :operations conj stop-op)

            (and can-manage? (utils/can-update? resource)) (update :operations conj update-op)

            (and can-manage? (utils/can-create-log? resource))
            (update :operations conj create-log-op)

            (and can-manage? can-clone?)
            (update :operations conj clone-op)

            (and can-manage? (utils/can-fetch-module? resource))
            (update :operations conj fetch-module-op)

            can-manage? (update :operations conj upcoming-invoice-op)

            can-manage? (update :operations conj check-dct-op)

            (not (utils/can-delete? resource))
            (update :operations utils/remove-delete))))


(defn edit-deployment
  [resource request edit-fn]
  (-> resource
      (edit-fn)
      (u/update-timestamps)
      (u/set-updated-by request)
      (db/edit request)))


(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id             (str resource-type "/" uuid)
          deployment     (-> (crud/retrieve-by-id-as-admin id)
                             (utils/throw-can-not-do-action utils/can-start? "start")
                             (utils/throw-can-not-access-registries-creds request))
          stopped?       (= (:state deployment) "STOPPED")
          price          (get-in deployment [:module :price])
          coupon         (:coupon deployment)
          subs-id        (when (and config-nuvla/*stripe-api-key* price)
                           (some-> (auth/current-active-claim request)
                                   (create-subscription price coupon)
                                   (stripe/get-id)))
          new-deployment (-> deployment
                             (edit-deployment
                               request
                               #(cond-> (assoc % :state "STARTING")
                                        subs-id (assoc :subscription-id subs-id)))
                             :body)]
      (when stopped?
        (utils/delete-child-resources "deployment-parameter" id))
      (utils/create-job new-deployment request "start_deployment"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (utils/throw-can-not-do-action utils/can-stop? "stop")
        (edit-deployment request #(assoc % :state "STOPPING"))
        :body
        (utils/create-job request "stop_deployment"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "create-log"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        (utils/throw-can-not-do-action utils/can-create-log? "create-log")
        (utils/create-log request))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "check-dct"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        (utils/create-job request "dct_check"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "clone"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/throw-cannot-view-data request))
      (create-deployment (assoc-in request [:body :deployment :href] id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn update-deployment-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [current         (-> (str resource-type "/" uuid)
                              (crud/retrieve-by-id-as-admin)
                              (a/throw-cannot-manage request)
                              (utils/throw-can-not-do-action
                                utils/can-update? "update_deployment"))
          current-subs-id (when config-nuvla/*stripe-api-key* (:subscription-id current))
          module-href     (get-in current [:module :href])
          ;; update price, license, etc. from source module during update
          {:keys [name description price license]} (utils/resolve-module
                                                     (assoc request
                                                       :body {:module {:href module-href}}))
          coupon          (:coupon current)
          new-subs-id     (when (and config-nuvla/*stripe-api-key* price)
                            (some-> (auth/current-active-claim request)
                                    (create-subscription price coupon)
                                    (stripe/get-id)))
          new             (-> current
                              (edit-deployment
                                request
                                #(cond-> (assoc % :state "UPDATING")
                                         name (assoc-in [:module :name] name)
                                         description (assoc-in [:module :description] description)
                                         price (assoc-in [:module :price] price)
                                         license (assoc-in [:module :license] license)
                                         new-subs-id (assoc :subscription-id new-subs-id)))
                              :body)]
      (some-> current-subs-id stripe/retrieve-subscription
              (stripe/cancel-subscription {"invoice_now" true}))
      (utils/create-job new request "update_deployment"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "update"]
  [request]
  (update-deployment-impl request))


(defmethod crud/do-action [resource-type "upcoming-invoice"]
  [{{uuid :uuid} :params :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (r/json-response
      (or (-> (str resource-type "/" uuid)
              (crud/retrieve-by-id-as-admin)
              (a/throw-cannot-manage request)
              :subscription-id
              (customer-utils/get-upcoming-invoice))
          {}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::deployment-spec/deployment))


(defn initialize
  []
  (std-crud/initialize resource-type ::deployment-spec/deployment)
  (md/register resource-metadata))
