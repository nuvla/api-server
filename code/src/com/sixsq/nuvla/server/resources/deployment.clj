(ns com.sixsq.nuvla.server.resources.deployment
  "
These resources represent the deployment of a component or application within
a container orchestration engine.
"
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.event-context :as ectx]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment.utils :as utils]
    [com.sixsq.nuvla.server.resources.job.interface :as job-interface]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.common-body :as common-body]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


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
               :output-message "application/json"}

              {:name           "detach"
               :uri            "detach"
               :description    "detach from deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])

(def validate-fn (u/create-spec-validation-fn ::deployment-spec/deployment))

(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn pre-delete-attrs-hook
  [current {body :body :as request}]
  (let [body-module          (:module body)
        dep-module           (:module current)
        module-href          (:href body-module)
        module-href-changed? (and module-href
                                  (not= module-href (get-in current [:module :href])))]
    (assoc current
      :module
      (cond
        module-href-changed? (utils/keep-module-defined-values
                               (or body-module dep-module)
                               (module-utils/resolve-module module-href request))
        body-module (utils/keep-module-defined-values body-module dep-module)
        :else dep-module))))

(defn pre-validate-hook
  [current {{:keys [parent] :as body} :body :as request}]
  (let [id             (:id current)
        cred-id        (or parent (:parent current))
        cred-edited?   (utils/cred-edited? parent (:parent current))
        cred           (some-> cred-id crud/retrieve-by-id-as-admin
                               (cond-> cred-edited? (a/throw-cannot-view request)))
        cred-name      (:name cred)
        infra-id       (:parent cred)
        infra          (some-> infra-id crud/retrieve-by-id-as-admin)
        infra-name     (:name infra)
        nb-id          (utils/infra->nb-id infra)
        nb             (some-> nb-id crud/retrieve-by-id-as-admin)
        nb-name        (:name nb)
        dep-set-id     (:deployment-set current)
        dep-set-name   (some-> dep-set-id crud/retrieve-by-id-as-admin :name)
        execution-mode (utils/get-execution-mode current body cred-id nb)
        new-acl        (utils/get-acl current body nb-id)
        acl-updated?   (not= new-acl (:acl current))]
    (when acl-updated?
      (utils/propagate-acl-to-dep-parameters id new-acl))
    (cond-> current
            parent (assoc :parent (:id cred))
            new-acl (assoc :acl new-acl)
            cred-name (assoc :credential-name cred-name)
            infra-id (assoc :infrastructure-service infra-id)
            infra-name (assoc :infrastructure-service-name infra-name)
            nb-id (assoc :nuvlabox nb-id)
            nb-name (assoc :nuvlabox-name nb-name)
            dep-set-name (assoc :deployment-set-name dep-set-name)
            execution-mode (assoc :execution-mode execution-mode))))

(defn create-deployment
  [{:keys [parent] :as deployment} {:keys [base-uri] :as request}]
  (some-> parent (crud/get-resource-throw-nok request))
  (let [
        ;; FIXME: Correct the value passed to the python API.
        body (-> deployment
                 (assoc :resource-type resource-type
                        :state "CREATED"
                        :api-endpoint (str/replace-first base-uri #"/api/" "")
                        :owner (auth/current-active-claim request))
                 (utils/throw-when-payment-required request)
                 (pre-validate-hook (dissoc request :body)))]
    (add-impl (assoc request :body body))))

(defmethod crud/add resource-type
  [{{:keys [parent execution-mode deployment-set app-set]} :body :as request}]
  ;; TODO only allow creation with specific version to always have a version without needing to check versions map
  (a/throw-cannot-add collection-acl request)
  (-> request
      module-utils/resolve-from-module
      (cond-> deployment-set (assoc :deployment-set deployment-set)
              app-set (assoc :app-set app-set)
              parent (assoc :parent parent)
              execution-mode (assoc :execution-mode execution-mode))
      (create-deployment request)))

(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))

(def edit-impl (std-crud/edit-fn resource-type
                                 :immutable-keys [:owner :infrastructure-service
                                                  :subscription-id :deployment-set
                                                  :module :parent]
                                 :pre-delete-attrs-hook pre-delete-attrs-hook
                                 :pre-validate-hook pre-validate-hook))

(defmethod crud/edit resource-type
  [request]
  (edit-impl request))

(defn delete-impl
  ([request]
   (delete-impl request false))
  ([{{uuid :uuid} :params :as request} force-delete]
   (try
     (let [deployment-id (str resource-type "/" uuid)
           deployment    (-> (crud/retrieve-by-id-as-admin deployment-id)
                             (a/throw-cannot-delete request)
                             (cond-> (not force-delete)
                                     (u/throw-cannot-do-action-invalid-state
                                       utils/can-delete? "delete")))]
       (ectx/add-to-context :acl (:acl deployment))
       (ectx/add-to-context :resource deployment)
       (utils/delete-all-child-resources deployment-id)
       (db/delete deployment))
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
  (let [start-op        (u/action-map id :start)
        stop-op         (u/action-map id :stop)
        update-op       (u/action-map id :update)
        create-log-op   (u/action-map id :create-log)
        clone-op        (u/action-map id :clone)
        check-dct-op    (u/action-map id :check-dct)
        fetch-module-op (u/action-map id :fetch-module)
        force-delete-op (u/action-map id :force-delete)
        detach-op       (u/action-map id :detach)
        can-manage?     (a/can-manage? resource request)
        can-edit-data?  (a/can-edit-data? resource request)
        can-clone?      (a/can-view-data? resource request)]
    (cond-> (crud/set-standard-operations resource request)

            (and can-manage? (utils/can-start? resource)) (update :operations conj start-op)

            (and can-manage? (utils/can-stop? resource))
            (update :operations conj stop-op)

            (and can-manage? (utils/can-update? resource)) (update :operations conj update-op)

            (and can-manage? (utils/can-create-log? resource))
            (update :operations conj create-log-op)

            (and can-manage? can-clone?)
            (update :operations conj clone-op)

            can-manage? (update :operations conj check-dct-op)

            (and can-manage? (utils/can-detach? resource)) (update :operations conj detach-op)

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
      crud/edit
      :body))

(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id             (str resource-type "/" uuid)
          deployment     (-> (crud/retrieve-by-id-as-admin id)
                             (u/throw-cannot-do-action-invalid-state utils/can-start? "start")
                             (utils/throw-when-payment-required request)
                             (utils/throw-can-not-access-registries-creds request)
                             (utils/throw-can-not-access-helm-repo-cred request)
                             (utils/throw-can-not-access-helm-repo-url request))
          stopped?       (= (:state deployment) "STOPPED")
          user-rights?   (get-in deployment [:module :content :requires-user-rights])
          data?          (some? (:data deployment))
          execution-mode (:execution-mode deployment)
          state          (if (= execution-mode "pull") "PENDING" "STARTING")
          new-deployment (-> deployment
                             (assoc :state state)

                             (assoc :api-credentials (utils/generate-api-key-secret
                                                       id
                                                       (when (or data?
                                                                 user-rights?)
                                                         (auth/current-authentication request))))
                             (edit-deployment request))]
      (when stopped?
        (utils/delete-child-resources "deployment-parameter" id))
      (utils/create-job new-deployment request "start_deployment" execution-mode))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [deployment     (-> (str resource-type "/" uuid)
                             (crud/retrieve-by-id-as-admin)
                             (u/throw-cannot-do-action-invalid-state utils/can-stop? "stop"))
          execution-mode (:execution-mode deployment)]
      (-> deployment
          (assoc :state "STOPPING")
          (edit-deployment request)
          (utils/create-job request "stop_deployment" execution-mode
                            :payload (when (and (a/can-delete? deployment request)
                                                (:delete body))
                                       {:delete true}))))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "create-log"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        (u/throw-cannot-do-action-invalid-state utils/can-create-log? "create-log")
        (utils/throw-when-payment-required request)
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
  [request]
  (try
    (a/throw-cannot-add collection-acl request)
    (let [response (-> (crud/get-resource-throw-nok request)
                       (select-keys [:module :data :name :description :tags])
                       (create-deployment request))]
      (ectx/add-linked-identifier (get-in response [:body :resource-id]))
      response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "force-delete"]
  [request]
  (delete-impl request true))

(defn update-deployment-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [current (-> (str resource-type "/" uuid)
                      (crud/retrieve-by-id-as-admin)
                      (a/throw-cannot-manage request)
                      (u/throw-cannot-do-action-invalid-state
                        utils/can-update? "update_deployment")
                      (utils/throw-when-payment-required request)
                      (utils/throw-can-not-access-registries-creds request)
                      (utils/throw-can-not-access-helm-repo-cred request)
                      (utils/throw-can-not-access-helm-repo-url request))
          new     (-> current
                      (assoc :state "UPDATING")
                      (edit-deployment request))]
      (utils/create-job new request "update_deployment" (:execution-mode new)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "update"]
  [request]
  (update-deployment-impl request))

(defmethod crud/do-action [resource-type "detach"]
  [{{uuid :uuid} :params :as _request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (u/throw-cannot-do-action utils/can-detach? "detach")
        (dissoc :deployment-set :deployment-set-name)
        u/update-timestamps
        db/edit)
    (r/map-response "detached" 200)
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

(defmethod job-interface/on-cancel ["deployment" "start_deployment"]
  [resource]
  (utils/on-cancel resource))

(defmethod job-interface/on-cancel ["deployment" "update_deployment"]
  [resource]
  (utils/on-cancel resource))

(defmethod job-interface/on-cancel ["deployment" "stop_deployment"]
  [resource]
  (utils/on-cancel resource))

(defmethod job-interface/on-done ["deployment" "stop_deployment"]
  [{:keys [target-resource state payload] :as _job}]
  (when-let [deployment (and (= state job-utils/state-success)
                             (-> payload json/read-str (get "delete" false))
                             (some-> target-resource :href crud/retrieve-by-id-as-admin))]
    (db/delete deployment)))

(def validate-edit-tags-body (u/create-spec-validation-request-body-fn
                               ::common-body/bulk-edit-tags-body))

(defn bulk-edit-tags
  [request bulk-impl]
  (-> request
      validate-edit-tags-body
      bulk-impl))

(def bulk-edit-impl (std-crud/bulk-edit-fn resource-type collection-acl))

(defmethod crud/bulk-action [resource-type "set-tags"]
  [request]
  (bulk-edit-tags request bulk-edit-impl))

(def bulk-add-impl (std-crud/bulk-edit-fn resource-type collection-acl :add))

(defmethod crud/bulk-action [resource-type "add-tags"]
  [request]
  (bulk-edit-tags request bulk-add-impl))

(def bulk-remove-impl (std-crud/bulk-edit-fn resource-type collection-acl :remove))

(defmethod crud/bulk-action [resource-type "remove-tags"]
  [request]
  (bulk-edit-tags request bulk-remove-impl))

(def bulk-action-impl (std-crud/bulk-action-fn resource-type collection-acl collection-type))

(defmethod crud/bulk-action [resource-type "bulk-update"]
  [request]
  (bulk-action-impl request))

(defmethod crud/bulk-action [resource-type "bulk-stop"]
  [request]
  (bulk-action-impl request))

(defmethod crud/bulk-action [resource-type "bulk-delete"]
  [request]
  (bulk-action-impl request))

(defmethod crud/bulk-action [resource-type "bulk-force-delete"]
  [request]
  (bulk-action-impl request))

(defmethod crud/do-action [resource-type "fetch-module"]
  [{{uuid :uuid} :params body :body :as request}]
  (let [id          (str resource-type "/" uuid)
        deployment  (-> (crud/retrieve-by-id-as-admin id)
                        (a/throw-cannot-edit request)
                        (utils/throw-when-payment-required request))
        module-href (:module-href body)
        [module-uuid version-index] (when (string? module-href)
                                      (module-utils/split-uuid module-href))]
    (when (or (not= module-uuid (-> deployment
                                    (get-in [:module :href])
                                    module-utils/full-uuid->uuid))
              (nil? version-index))
      (throw (r/ex-response "invalid module-href" 400)))
    (crud/edit {:params      {:uuid          uuid
                              :resource-name resource-type}
                :body        (assoc-in deployment [:module :href] module-href)
                :nuvla/authn (auth/current-authentication request)})))

;;
;; Events
;;

(defmethod ec/events-enabled? resource-type
  [_resource-type]
  true)

(defmethod ec/log-event? (str resource-type ".add")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".edit")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".delete")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".start")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".update")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".stop")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".clone")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".force-delete")
  [_event _response]
  true)

(defmethod ec/event-description (str resource-type ".start")
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " started deployment"))
    "Deployment start attempt failed"))

(defmethod ec/event-description (str resource-type ".update")
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " updated deployment"))
    "Deployment update attempt failed"))


(defmethod ec/event-description (str resource-type ".stop")
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " stopped deployment"))
    "Deployment stop attempt failed"))


(defmethod ec/event-description (str resource-type ".clone")
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " cloned deployment"))
    "Deployment clone attempt failed"))

(def resource-metadata (gen-md/generate-metadata ::ns ::deployment-spec/deployment))

(defn initialize
  []
  (std-crud/initialize resource-type ::deployment-spec/deployment)
  (md/register resource-metadata))
