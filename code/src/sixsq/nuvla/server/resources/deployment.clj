(ns sixsq.nuvla.server.resources.deployment
  "
These resources represent the deployment of a component or application within
a container orchestration engine.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment.utils :as utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-action ["group/nuvla-user"]})


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

               :input-parameters [{:name "service"
                                   :type "string"}

                                  {:name "since"
                                   :type "date-time"}

                                  {:name        "lines"
                                   :type        "integer"
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

              {:name           "check-dct"
               :uri            "check-dct"
               :description    "check if images are trusted"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "force-delete"
               :uri            "force-delete"
               :description    "delete deployment forcefully without checking state and without stopping it"
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


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn create-deployment
  [{:keys [base-uri] {:keys [owner]} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (user-utils/throw-user-hasnt-active-subscription request)
  (let [authn-info      (auth/current-authentication request)
        is-admin?       (a/is-admin? authn-info)
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
  [{{:keys [acl parent module]} :body {uuid :uuid} :params :as request}]
  (let [id           (str resource-type "/" uuid)
        current      (db/retrieve id request)
        authn-info   (auth/current-authentication request)
        cred-id      (or parent (:parent current))
        cred         (utils/some-id->resource cred-id request)
        infra-id     (:parent cred)
        cred-name    (:name cred)
        infra        (utils/some-id->resource infra-id request)
        infra-name   (:name cred)
        nb-id        (utils/infra->nb-id infra request)
        nb-name      (:name (utils/some-id->resource nb-id request))
        fixed-attr   (select-keys (:module current) [:href :price :license])
        is-user?     (not (a/is-admin? authn-info))
        new-acl      (-> (or acl (:acl current))
                         (a/acl-append :owners (:owner current))
                         (a/acl-append :view-acl id)
                         (a/acl-append :edit-data id)
                         (a/acl-append :edit-data nb-id)
                         (cond->
                           (and (some? (:nuvlabox current))
                                (not= nb-id (:nuvlabox current)))
                           (a/acl-remove (:nuvlabox current))))
        acl-updated? (not= new-acl (:acl current))]
    (when acl-updated?
      (utils/propagate-acl-to-dep-parameters id new-acl))
    (edit-impl
      (cond-> request
              is-user? (update :body dissoc :owner :infrastructure-service :subscription-id
                               :nuvlabox)
              (and is-user? module) (update-in [:body :module] merge fixed-attr)
              is-user? (update-in [:cimi-params :select] disj
                                  "owner" "infrastructure-service" "module/price"
                                  "module/license" "subscription-id")
              new-acl (assoc-in [:body :acl] new-acl)
              cred-name (assoc-in [:body :credential-name] cred-name)
              infra-id (assoc-in [:body :infrastructure-service] infra-id)
              infra-name (assoc-in [:body :infrastructure-service-name] infra-name)
              nb-id (assoc-in [:body :nuvlabox] nb-id)
              nb-name (assoc-in [:body :nuvlabox-name] nb-name)
              ))))


(defn delete-impl
  ([request]
   (delete-impl request false))
  ([{{uuid :uuid} :params :as request} force-delete]
   (try
     (let [deployment-id   (str resource-type "/" uuid)
           deployment      (db/retrieve deployment-id request)
           _               (when-not force-delete
                             (utils/throw-can-not-do-action deployment utils/can-delete? "delete"))
           delete-response (-> deployment
                               (a/throw-cannot-delete request)
                               (db/delete request))]
       (when force-delete
         (utils/stop-subscription deployment))
       (utils/delete-all-child-resources deployment-id)
       delete-response)
     (catch Exception e
       (or (ex-data e) (throw e))))))


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
        check-dct-op        (u/action-map id :check-dct)
        fetch-module-op     (u/action-map id :fetch-module)
        upcoming-invoice-op (u/action-map id :upcoming-invoice)
        force-delete-op     (u/action-map id :force-delete)
        can-manage?         (a/can-manage? resource request)
        can-edit-data?      (a/can-edit-data? resource request)
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

            can-manage? (update :operations conj upcoming-invoice-op)

            can-manage? (update :operations conj check-dct-op)

            (and can-manage? can-edit-data?) (update :operations conj fetch-module-op)

            (a/can-delete? resource request) (update :operations conj force-delete-op)

            (not (utils/can-delete? resource))
            (update :operations utils/remove-delete))))


(defn edit-deployment
  [resource request]
  (-> request
      (assoc :request-method :put
             :body resource
             :nuvla/authn auth/internal-identity)
      (crud/edit)
      :body))


(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id             (str resource-type "/" uuid)
          deployment     (-> (crud/retrieve-by-id-as-admin id)
                             (utils/throw-can-not-do-action utils/can-start? "start")
                             (utils/throw-can-not-access-registries-creds request))
          stopped?       (= (:state deployment) "STOPPED")
          price          (get-in deployment [:module :price])
          user-rights?   (get-in deployment [:module :content :requires-user-rights])
          data?          (some? (:data deployment))
          subs-id        (utils/create-subscription request deployment price)
          execution-mode (:execution-mode deployment)
          state          (if (= execution-mode "pull") "PENDING" "STARTING")
          new-deployment (-> deployment
                             (assoc :state state)

                             (assoc :api-credentials (utils/generate-api-key-secret
                                                       id
                                                       (when (or data?
                                                                 user-rights?)
                                                         (auth/current-authentication request))))
                             (cond-> subs-id (assoc :subscription-id subs-id))
                             (edit-deployment request))]
      (when stopped?
        (utils/delete-child-resources "deployment-parameter" id))
      (utils/create-job new-deployment request "start_deployment" execution-mode))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [deployment     (-> (str resource-type "/" uuid)
                             (crud/retrieve-by-id-as-admin)
                             (utils/throw-can-not-do-action utils/can-stop? "stop"))
          execution-mode (:execution-mode deployment)
          response       (-> deployment
                             (assoc :state "STOPPING")
                             (edit-deployment request)
                             (utils/create-job request "stop_deployment" execution-mode))]
      (utils/stop-subscription deployment)
      response)
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
        (utils/create-job request "dct_check" "push"))
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


(defmethod crud/do-action [resource-type "force-delete"]
  [request]
  (delete-impl request true))


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
          new-subs-id     (utils/create-subscription request current price)
          new             (-> current
                              (assoc :state "UPDATING")
                              (cond-> name (assoc-in [:module :name] name)
                                      description (assoc-in [:module :description] description)
                                      price (assoc-in [:module :price] price)
                                      license (assoc-in [:module :license] license)
                                      new-subs-id (assoc :subscription-id new-subs-id))
                              (edit-deployment request))]
      (some-> current-subs-id pricing-impl/retrieve-subscription
              (pricing-impl/cancel-subscription {"invoice_now" true}))
      (utils/create-job new request "update_deployment" (:execution-mode new)))
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
              (pricing-impl/get-upcoming-invoice))
          {}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod job-interface/get-context ["deployment" "start_deployment"]
  [resource]
  (utils/get-context resource true))


(defmethod job-interface/get-context ["deployment" "update_deployment"]
  [resource]
  (utils/get-context resource true))


(defmethod job-interface/get-context ["deployment" "stop_deployment"]
  [resource]
  (utils/get-context resource false))


(defmethod job-interface/get-context ["deployment" "deployment_state"]
  [resource]
  (utils/get-context resource false))

(defmethod job-interface/get-context ["deployment" "deployment_state_10"]
  [resource]
  (utils/get-context resource false))

(defmethod job-interface/get-context ["deployment" "deployment_state_60"]
  [resource]
  (utils/get-context resource false))


(def bulk-action-impl (std-crud/bulk-action-fn resource-type collection-acl collection-type))

(defmethod crud/bulk-action [resource-type "bulk-update"]
  [request]
  (bulk-action-impl request))

(defmethod crud/bulk-action [resource-type "bulk-stop"]
  [request]
  (bulk-action-impl request))


(defmethod crud/bulk-action [resource-type "bulk-force-delete"]
  [request]
  (bulk-action-impl request))


(defmethod crud/do-action [resource-type "fetch-module"]
  [{{uuid :uuid} :params body :body :as request}]
  (let [id          (str resource-type "/" uuid)
        deployment  (crud/retrieve-by-id-as-admin id)
        module-href (:module-href body)]
    (a/throw-cannot-edit deployment request)
    (when (or (not (string? module-href))
              (str/blank? module-href)
              (not (str/starts-with? module-href (-> deployment
                                                     (get-in [:module :href])
                                                     (str/split #"_")
                                                     first))))
      (throw (r/ex-response "invalid module-href" 400)))
    (let [authn-info  (auth/current-authentication request)
          module      (utils/resolve-module (assoc request :body {:module {:href module-href}}))
          dep-updated (update deployment :module utils/merge-module module)]
      (crud/edit {:params      {:uuid          uuid
                                :resource-name resource-type}
                  :body        dep-updated
                  :nuvla/authn authn-info}))))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::deployment-spec/deployment))


(defn initialize
  []
  (std-crud/initialize resource-type ::deployment-spec/deployment)
  (md/register resource-metadata))
