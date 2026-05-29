 (ns com.sixsq.nuvla.server.resources.mec.app-lcm
   "MEC 010-2 Application Lifecycle Management API
   
   Provides RESTful endpoints compliant with ETSI GS MEC 010-2 v4.1.1
   for application lifecycle management at the MEO level.
   
   Canonical API tree:
   - app_lcm/v1
   
   Endpoints:
   - POST   /app_lcm/v1/app_instances               - Create app instance
   - GET    /app_lcm/v1/app_instances               - List app instances
   - GET    /app_lcm/v1/app_instances/{id}          - Get app instance
   - DELETE /app_lcm/v1/app_instances/{id}          - Delete app instance
   - POST   /app_lcm/v1/app_instances/{id}/instantiate - Instantiate
   - POST   /app_lcm/v1/app_instances/{id}/terminate   - Terminate
   - POST   /app_lcm/v1/app_instances/{id}/operate     - Start/Stop
   - GET    /app_lcm/v1/app_lcm_op_occs             - List operations
   - GET    /app_lcm/v1/app_lcm_op_occs/{id}        - Get operation
   - POST   /app_lcm/v1/subscriptions               - Create subscription
   - GET    /app_lcm/v1/subscriptions               - List subscriptions
   - GET    /app_lcm/v1/subscriptions/{id}          - Get subscription
   - DELETE /app_lcm/v1/subscriptions/{id}          - Delete subscription"
   (:require
     [clojure.string :as str]
     [clojure.tools.logging :as log]
     [com.sixsq.nuvla.auth.utils :as auth]
     [com.sixsq.nuvla.db.filter.parser :as parser]
     [com.sixsq.nuvla.server.resources.common.crud :as crud]
     [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment-parameter :as deployment-parameter]
    [com.sixsq.nuvla.server.resources.deployment.utils :as deployment-utils]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
     [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
     [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
     [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
     [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
     [com.sixsq.nuvla.server.resources.mepm :as mepm-resource]
     [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]
     [com.sixsq.nuvla.server.resources.mec.query-filter :as qf]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as time-utils]
    [ring.util.response :as rur]))


 ;;
 ;; Resource Type Definition
 ;;

 (def ^:const resource-type "mec-app-lcm")
 (def ^:const collection-type "mec-app-lcm-collection")
 (def ^:const api-version "v1")
 (def ^:const base-uri (str "app_lcm/" api-version))
 (def ^:private public-base-uri "/mec/mm1/app_lcm/v1")

(defn- request-public-base-uri
  [request]
  (if-let [base-uri (:base-uri request)]
    (str base-uri "mec/mm1/app_lcm/v1")
    public-base-uri))

(defn- op-occ-location
  [request op-occ-id]
  (str (request-public-base-uri request) "/app_lcm_op_occs/" op-occ-id))


 ;;
 ;; Error Handling (RFC 7807 ProblemDetails)
 ;;

 (defn problem-details
   "Creates an RFC 7807 ProblemDetails error response"
   [type title status & {:keys [detail instance]}]
   {:type     (or type "about:blank")
    :title    title
    :status   status
    :detail   (or detail title)
    :instance instance})


 (defn not-found-error
   "Returns a 404 Not Found error in ProblemDetails format"
   [resource-id]
   (problem-details
     "https://docs.nuvla.io/mec/errors/not-found"
     "Resource Not Found"
     404
     :detail (str "App instance " resource-id " not found")
     :instance resource-id))


 (defn validation-error
   "Returns a 400 Bad Request error in ProblemDetails format"
   [detail]
   (problem-details
     "https://docs.nuvla.io/mec/errors/validation"
     "Validation Error"
     400
     :detail detail))


 (defn conflict-error
   "Returns a 409 Conflict error in ProblemDetails format"
   [detail]
   (problem-details
     "https://docs.nuvla.io/mec/errors/conflict"
     "Resource Conflict"
     409
     :detail detail))


 (defn service-unavailable-error
   "Returns a 503 Service Unavailable error in ProblemDetails format"
   [detail]
   (problem-details
     "https://docs.nuvla.io/mec/errors/service-unavailable"
     "Service Unavailable"
     503
     :detail detail))


 (def ^:private max-query-results
   1000)


 (def ^:private mec-operation-types
   #{"INSTANTIATE" "TERMINATE" "OPERATE"})


 (def ^:private eligible-mepm-statuses
   #{"ONLINE" "DEGRADED"})

(def ^:private allowed-cancel-modes
  #{"GRACEFUL" "FORCEFUL"})

(def ^:private default-mepm-backend-mode
  "MOCK")

(def ^:private southbound-app-instance-param-name
  "mec.app-instance-id")

(declare job->northbound-op-occ)


 (defn- exception-status
   [e default-status]
   (or (:status (ex-data e)) default-status))


 (defn- exception-response
   [e default-status]
   (let [status (exception-status e default-status)
         detail (or (ex-message e) "Unexpected error")]
     (-> (case status
           404 (problem-details
                 "https://docs.nuvla.io/mec/errors/not-found"
                 "Resource Not Found"
                 404
                 :detail detail)
           409 (conflict-error detail)
           503 (service-unavailable-error detail)
           500 (problem-details
                 "about:blank"
                 "Internal Server Error"
                 500
                 :detail detail)
           (validation-error detail))
         r/json-response
         (assoc :status status))))


 (defn- json-response-status
   [body status]
   (-> body
       r/json-response
       (assoc :status status)))


 (defn- resource-query-request
   [request resource-name]
   {:params      {:resource-name resource-name}
    :cimi-params {:last max-query-results}
    :nuvla/authn (:nuvla/authn request)})


 (defn- query-resources
   [request resource-name]
   (-> (resource-query-request request resource-name)
       crud/query
       :body
       :resources
       (or [])))


 (defn- query-resources-as-admin
   [resource-name & [filter-str]]
   (-> {:params      {:resource-name resource-name}
        :cimi-params (cond-> {:last max-query-results}
                       filter-str (assoc :filter (parser/parse-cimi-filter filter-str)))
        :nuvla/authn auth/internal-identity}
       crud/query
       :body
       :resources
       (or [])))


 (defn- mec-operation-job?
   [job]
   (contains? mec-operation-types
              (some-> (or (:mec-operation-type job)
                          (:operation-type job))
                      str/upper-case)))


 (defn- throw-status
   [status detail & [data]]
   (throw (ex-info detail (merge {:status status} data))))

(defn- ensure-mec-operation-job!
  [request lcm-op-occ-id]
  (let [job (crud/get-resource-throw-nok lcm-op-occ-id request)]
    (when-not (mec-operation-job? job)
      (throw-status 404 (str lcm-op-occ-id " not found")
                    {:lcm-op-occ-id lcm-op-occ-id}))
    job))

(defn- update-operation-job!
  [request lcm-op-occ-id updates]
  (let [now       (time-utils/now-str)
        body      (merge {:updated            now
                          :state-entered-time now
                          :time-of-status-change now}
                         updates)
        _         (crud/edit-by-id-as-admin lcm-op-occ-id body)
        job       (crud/retrieve-by-id-as-admin lcm-op-occ-id)
        op-occ    (job->northbound-op-occ job request)]
    (dispatcher/dispatch-app-lcm-op-occ-state-change! op-occ "OPERATION_STATE" nil)
    op-occ))

(defn- validate-cancel-request!
  [body]
  (let [body             (or body {})
        unsupported-keys (seq (remove #{:CancelMode} (keys body)))
        cancel-mode      (some-> (:CancelMode body) str/upper-case)]
    (when unsupported-keys
      (throw-status 400
                    (str "Unsupported cancel request fields: "
                         (str/join ", " (map name unsupported-keys)))
                    {:unsupported-fields unsupported-keys}))
    (when-not (contains? allowed-cancel-modes cancel-mode)
      (throw-status 400 "CancelMode must be GRACEFUL or FORCEFUL"
                    {:cancel-mode (:CancelMode body)}))
    (assoc body :CancelMode cancel-mode)))


 (defn- retrieve-app-instance-deployment
   [request app-instance-id]
   (crud/get-resource-throw-nok app-instance-id request))


 (defn- retrieve-app-instance-info
   [request app-instance-id]
   (->> app-instance-id
        (retrieve-app-instance-deployment request)
       (#(app-instance/deployment->app-instance-info % request))))

(defn- target-host-id
  [deployment]
  (or (:mec-host-id deployment)
      (:nuvlabox deployment)
      (:parent deployment)))


 (defn- request-change-state->target-state
   [change-state]
   (cond
     (keyword? change-state) (-> change-state name str/upper-case)
     (string? change-state) (str/upper-case change-state)
     :else nil))


(def ^:private allowed-instantiate-request-keys
  #{:selectedMECHostInfo :grantId})

(def ^:private allowed-terminate-request-keys
  #{:terminationType :gracefulTerminationTimeout})

(defn- validate-instantiate-request!
  [request app-instance-id body]
   (let [{:keys [instantiationState]} (retrieve-app-instance-info request app-instance-id)]
     (when-not (= "NOT_INSTANTIATED" instantiationState)
       (throw-status 409 "App instance must be NOT_INSTANTIATED before instantiation"
                     {:app-instance-id app-instance-id
                     :instantiation-state instantiationState})))
  (let [unsupported-keys (seq (remove allowed-instantiate-request-keys (keys (or body {}))))]
    (when unsupported-keys
      (throw-status 400
                    (str "Unsupported instantiate request fields: "
                         (str/join ", " (map name unsupported-keys)))
                    {:app-instance-id app-instance-id
                     :unsupported-fields unsupported-keys})))
  body)


 (defn- validate-terminate-request!
  [request app-instance-id body]
  (let [unsupported-keys (seq (remove allowed-terminate-request-keys (keys (or body {}))))]
    (when unsupported-keys
      (throw-status 400
                    (str "Unsupported terminate request fields: "
                         (str/join ", " (map name unsupported-keys)))
                    {:app-instance-id app-instance-id
                     :unsupported-fields unsupported-keys})))
   (let [{:keys [instantiationState]} (retrieve-app-instance-info request app-instance-id)]
     (when (= "NOT_INSTANTIATED" instantiationState)
       (throw-status 409 "App instance must be INSTANTIATED before termination"
                     {:app-instance-id app-instance-id
                     :instantiation-state instantiationState})))
  body)


 (defn- validate-operate-request!
   [request app-instance-id body]
   (let [deployment   (retrieve-app-instance-deployment request app-instance-id)
         target-state (request-change-state->target-state (:changeStateTo body))
         current-state (:state deployment)]
     (when-not (#{"STARTED" "STOPPED"} target-state)
       (throw-status 400 "changeStateTo must be STARTED or STOPPED"
                     {:app-instance-id app-instance-id
                      :change-state-to (:changeStateTo body)}))
     (case target-state
       "STARTED"
       (when-not (= "STOPPED" current-state)
         (throw-status 409 "Operate STARTED requires the app instance to be STOPPED"
                       {:app-instance-id app-instance-id
                        :current-state current-state
                        :target-state target-state}))
       "STOPPED"
       (when-not (= "STARTED" current-state)
         (throw-status 409 "Operate STOPPED requires the app instance to be STARTED"
                       {:app-instance-id app-instance-id
                        :current-state current-state
                        :target-state target-state})))
     (assoc body :changeStateTo target-state)))


 (defn- deployment-action-request
   [request app-instance-id action body job-attrs]
   (cond-> {:params      {:resource-name "deployment"
                          :uuid          (u/id->uuid app-instance-id)
                          :action        action}
            :nuvla/authn (:nuvla/authn request)}
     (or (seq body) (seq job-attrs))
     (assoc :body (cond-> (or body {})
                    (seq job-attrs) (assoc :job-attrs job-attrs)))))


 (defn- throw-on-unsuccessful-mm3-response!
   [response endpoint operation]
   (when-not (:success? response)
     (throw-status 503
                   (str "MEPM " operation " failed: " (:message response))
                   {:mepm-endpoint endpoint
                    :operation operation
                    :error (:error response)}))
   response)

(defn- mepm-manages-edge?
  [mepm edge-id]
  (contains? (set (mepm-resource/managed-edge-ids mepm)) edge-id))

(defn- eligible-mepms-for-host
  [edge-id]
  (let [eligible? #(contains? eligible-mepm-statuses (:status %))]
    (cond->> (query-resources-as-admin "mepm")
      true (filter eligible?)
      edge-id (filter #(mepm-manages-edge? % edge-id)))))


(defn- filter-by-persisted-mepm-id
  [eligible persisted-mepm-id]
  (if persisted-mepm-id
    (filterv #(= persisted-mepm-id (:id %)) eligible)
    (vec eligible)))


 (defn- resolve-mepm-for-deployment!
   [deployment]
  (let [host-id            (target-host-id deployment)
        persisted-mepm-id  (:mec-mepm-id deployment)
        eligible           (eligible-mepms-for-host host-id)
        narrowed-eligible  (filter-by-persisted-mepm-id eligible persisted-mepm-id)]
     (cond
      (and host-id (= 1 (count narrowed-eligible)))
      (assoc (first narrowed-eligible) :selected-mec-host-id host-id)

       (and host-id (empty? narrowed-eligible))
       (throw-status 503
                     (str "No eligible MEPM found for target NuvlaBox " host-id)
                     {:nuvlabox-id host-id
                      :mec-mepm-id persisted-mepm-id})
       (and host-id (> (count narrowed-eligible) 1))
       (throw-status 409
                     (str "Multiple eligible MEPMs found for target NuvlaBox " host-id)
                     {:nuvlabox-id host-id
                      :mec-mepm-id persisted-mepm-id})
       (= 1 (count narrowed-eligible)) (first narrowed-eligible)
       (empty? narrowed-eligible) (throw-status 503 "No eligible MEPM available")
       :else (throw-status 409 "Multiple eligible MEPMs available; explicit host targeting is required"))))

(defn- correlation-snapshot-for-create
  [deployment-id deployment-body]
  (let [host-id   (target-host-id deployment-body)
        mepms     (when host-id
                    (eligible-mepms-for-host host-id))
        mepm      (when (= 1 (count mepms))
                    (first mepms))]
    (cond-> {:mec-app-instance-id deployment-id}
      host-id (assoc :mec-host-id host-id)
      (:id mepm) (assoc :mec-mepm-id (:id mepm))
      (:name mepm) (assoc :mec-mepm-name (:name mepm))
      mepm (assoc :mec-backend-mode (or (:backend-mode mepm)
                                        default-mepm-backend-mode)))))


 (defn- resolve-mepm-for-app-instance!
   [request app-instance-id]
   (let [deployment (retrieve-app-instance-deployment request app-instance-id)
        mepm       (resolve-mepm-for-deployment! deployment)
        endpoint   (:endpoint mepm)
        _          (-> (mm3/query-capabilities endpoint)
                       (throw-on-unsuccessful-mm3-response! endpoint "capability query"))
        _          (-> (mm3/query-resources endpoint)
                       (throw-on-unsuccessful-mm3-response! endpoint "resource query"))]
    mepm))


 (defn- action-response->job-id
   [response]
   (or (get-in response [:body :location])
       (get-in response [:headers "Location"])))


(defn- upsert-deployment-parameter!
  [deployment-id name value]
  (let [parameter {:parent deployment-id
                   :name   name
                   :value  value}
        id        (deployment-parameter/parameter->id parameter)
        existing  (try
                    (crud/retrieve-by-id-as-admin id)
                    (catch Exception _
                      nil))]
    (if existing
      (crud/edit-by-id-as-admin id {:value value})
      (crud/add {:params      {:resource-name deployment-parameter/resource-type}
                 :body        parameter
                 :nuvla/authn auth/internal-identity}))
    (crud/retrieve-by-id-as-admin id)))


(defn- persisted-southbound-app-instance-id
  [deployment]
  (or (:mec-backing-deployment-id deployment)
      (try
        (some-> (crud/retrieve-by-id-as-admin
                 (deployment-parameter/parameter->id {:parent (:id deployment)
                                                      :name   southbound-app-instance-param-name}))
                :value)
        (catch Exception _
          nil))))


(defn- persist-southbound-app-instance-correlation!
  [deployment southbound-app-instance-id]
  (when (and southbound-app-instance-id
             (not= southbound-app-instance-id (:id deployment)))
    (if (str/starts-with? southbound-app-instance-id "deployment/")
      (crud/edit-by-id-as-admin (:id deployment)
                                {:mec-backing-deployment-id southbound-app-instance-id})
      (upsert-deployment-parameter! (:id deployment)
                                    southbound-app-instance-param-name
                                    southbound-app-instance-id))))


(def ^:private final-job-states
  #{"SUCCESS" "FAILED" "STOPPED" "CANCELED"})


(defn- southbound-operation-state
  [payload]
  (or (:operationState payload)
      (:operation-state payload)
      (:status payload)))


(defn- overlay-southbound-operation-status
  [job op-occ]
  (let [endpoint     (:mepm-endpoint job)
        operation-id (:mec-southbound-operation-id job)]
    (if (and endpoint
             operation-id
             (not (contains? final-job-states (:state job))))
      (try
        (let [response         (mm3/get-operation endpoint operation-id {:retry-attempts 1})
              operation-state  (some-> response :data southbound-operation-state str/upper-case)]
          (if (and (:success? response)
                   operation-state
                   (app-lcm-op-occ/valid-operation-state? operation-state))
            (assoc op-occ :operationState operation-state)
            op-occ))
        (catch Exception e
          (log/warn e "Failed to reconcile southbound operation status" operation-id)
          op-occ))
      op-occ)))


(defn- job->northbound-op-occ
  ([job]
   (job->northbound-op-occ job nil))
  ([job request]
   (let [op-occ (app-lcm-op-occ/job->app-lcm-op-occ
                  job
                  {:base-uri (request-public-base-uri request)})]
     (overlay-southbound-operation-status job op-occ))))


(defn- create-southbound-operation-job!
  [request deployment action operation-type request-params metadata]
   (let [job-attrs (cond-> {:mec-operation-type  operation-type
                           :mec-app-instance-id (:id deployment)}
                     (some? request-params) (assoc :mec-request-params request-params)
                     (some? (:mepm-id metadata)) (assoc :mepm-id (:mepm-id metadata))
                     (some? (:mepm-endpoint metadata)) (assoc :mepm-endpoint (:mepm-endpoint metadata))
                     (some? (:mec-host-id metadata)) (assoc :mec-host-id (:mec-host-id metadata)))
        response (crud/do-action (deployment-action-request request
                                                            (:id deployment)
                                                            action
                                                            nil
                                                            job-attrs))
        status   (:status response)]
     (when-not (= 202 status)
       (throw-status (or status 500)
                    (or (get-in response [:body :detail])
                        (get-in response [:body :message])
                        "Lifecycle job creation failed")
                    {:app-instance-id (:id deployment)
                     :operation-type operation-type}))
    (let [job-id (action-response->job-id response)]
      (when-not job-id
        (throw-status 500 "Lifecycle action did not return a job identifier"
                      {:app-instance-id (:id deployment)
                       :operation-type operation-type}))
      job-id)))


(defn- submit-southbound-lifecycle-operation!
  [request app-instance-id action operation-type request-params mepm]
  (let [deployment   (retrieve-app-instance-deployment request app-instance-id)
        endpoint     (:endpoint mepm)
        metadata     {:mepm-id       (:id mepm)
                      :mepm-endpoint endpoint
                      :mec-host-id   (:selected-mec-host-id mepm)}
        job-id       (create-southbound-operation-job! request deployment action operation-type request-params metadata)
        job          (crud/retrieve-by-id-as-admin job-id)]
    (job->northbound-op-occ job request)))


(def ^:private lifecycle-operation-config
  {"INSTANTIATE" {:log-prefix "Instantiate request for"
                  :validate   (fn [request app-instance-id body]
                                (validate-instantiate-request! request app-instance-id body))
                  :action     (constantly "start")}
   "TERMINATE"   {:log-prefix "Terminate request for"
                  :validate   (fn [request app-instance-id body]
                                (validate-terminate-request! request app-instance-id body))
                  :action     (constantly "stop")}
   "OPERATE"     {:log-prefix "Operate request for"
                  :validate   validate-operate-request!
                  :action     (fn [body]
                                (if (= "STARTED" (:changeStateTo body))
                                  "start"
                                  "stop"))}})


(defn submit-lifecycle-operation!
  [request operation-type]
  (let [app-instance-id (get-in request [:params :id])
        body            (:body request)
        {:keys [log-prefix validate action]} (get lifecycle-operation-config operation-type)]
    (when-not validate
      (throw-status 500
                    (str "Unsupported lifecycle operation type " operation-type)
                    {:operation-type operation-type
                     :app-instance-id app-instance-id}))
    (let [validated-body (validate request app-instance-id body)
          mepm           (resolve-mepm-for-app-instance! request app-instance-id)
          action-name    (action validated-body)]
      (if (= "OPERATE" operation-type)
        (log/info log-prefix app-instance-id "changeStateTo" (:changeStateTo validated-body))
        (log/info log-prefix app-instance-id))
      (submit-southbound-lifecycle-operation! request
                                              app-instance-id
                                              action-name
                                              operation-type
                                              validated-body
                                              mepm))))


 (defn create-app-instance-handler
   "POST /app_lcm/v1/app_instances - Create a new app instance"
   [request]
   (try
     (let [body              (:body request)
           _                 (app-instance/validate-create-app-instance-request body)
           deployment-body   (app-instance/create-request->deployment body)
           create-response   (crud/add (assoc request
                                         :params {:resource-name "deployment"}
                                         :body deployment-body))
           deployment-id     (get-in create-response [:body :resource-id])
           correlation       (correlation-snapshot-for-create deployment-id deployment-body)
           _                 (when (seq correlation)
                               (crud/edit-by-id-as-admin deployment-id correlation))
           deployment        (crud/get-resource-throw-nok deployment-id request)
          app-instance-info (app-instance/deployment->app-instance-info deployment request)
          location         (str (request-public-base-uri request) "/app_instances/" deployment-id)]
       (dispatcher/dispatch-app-instance-state-change! app-instance-info "INSTANTIATION_STATE" nil)
      (-> (json-response-status app-instance-info 201)
          (rur/header "Location" location)))
     (catch clojure.lang.ExceptionInfo e
       (log/error e "Failed to create app instance")
       (exception-response e 400))
     (catch Exception e
       (log/error e "Unexpected error creating app instance")
       (exception-response e 500))))


 (defn list-app-instances-handler
   "GET /app_lcm/v1/app_instances - List all app instances"
   [request]
   (try
    (let [params      (:params request)
          base-uri    (request-public-base-uri request)
           deployments (query-resources request "deployment")
          resources   (mapv #(app-instance/deployment->app-instance-info % request) deployments)]
       (r/json-response (qf/process-query resources
                                          (merge params
                                                {:base-uri (str base-uri "/app_instances")}))))
     (catch clojure.lang.ExceptionInfo e
       (log/error e "Failed to list app instances")
       (exception-response e 400))
     (catch Exception e
       (log/error e "Unexpected error listing app instances")
       (exception-response e 500))))


 (defn get-app-instance-handler
   "GET /app_lcm/v1/app_instances/{id} - Get a specific app instance"
   [request]
   (let [app-instance-id (get-in request [:params :id])]
     (try
       (-> app-instance-id
           (crud/get-resource-throw-nok request)
          (app-instance/deployment->app-instance-info request)
           r/json-response)
       (catch clojure.lang.ExceptionInfo e
         (log/error e "Failed to get app instance" app-instance-id)
         (exception-response e 404))
       (catch Exception e
         (log/error e "Unexpected error getting app instance" app-instance-id)
         (exception-response e 500)))))


 (defn delete-app-instance-handler
   "DELETE /app_lcm/v1/app_instances/{id} - Delete an app instance"
   [request]
   (let [app-instance-id (get-in request [:params :id])]
     (try
       (let [deployment        (crud/get-resource-throw-nok app-instance-id request)
            app-instance-info (app-instance/deployment->app-instance-info deployment request)
             inst-state        (:instantiationState app-instance-info)
             [_ uuid]          (u/parse-id app-instance-id)]
         (when-not (= "NOT_INSTANTIATED" inst-state)
           (throw (ex-info "App instance must be NOT_INSTANTIATED before deletion"
                           {:status 409
                            :app-instance-id app-instance-id
                            :instantiation-state inst-state})))
         (crud/delete {:params      {:resource-name "deployment"
                                     :uuid          uuid}
                       :nuvla/authn (:nuvla/authn request)})
         {:status 204
          :body   nil})
       (catch clojure.lang.ExceptionInfo e
         (log/error e "Failed to delete app instance" app-instance-id)
         (exception-response e 404))
       (catch Exception e
         (log/error e "Unexpected error deleting app instance" app-instance-id)
         (exception-response e 500)))))


 (defn instantiate-app-instance-handler
   "POST /app_lcm/v1/app_instances/{id}/instantiate - Instantiate an app"
   [request]
   (let [app-instance-id (get-in request [:params :id])]
     (try
       (let [op-occ (submit-lifecycle-operation! request "INSTANTIATE")]
         (log/info "Instantiation operation created:" (:lcmOpOccId op-occ))
        (-> (json-response-status op-occ 202)
            (rur/header "Location" (op-occ-location request (:lcmOpOccId op-occ)))))
       (catch clojure.lang.ExceptionInfo e
         (log/error e "Failed to instantiate app instance" app-instance-id)
         (exception-response e 400))
       (catch Exception e
         (log/error e "Unexpected error during instantiation" app-instance-id)
         (exception-response e 500)))))


 (defn terminate-app-instance-handler
   "POST /app_lcm/v1/app_instances/{id}/terminate - Terminate an app"
   [request]
   (let [app-instance-id (get-in request [:params :id])]
     (try
       (let [op-occ (submit-lifecycle-operation! request "TERMINATE")]
         (log/info "Termination operation created:" (:lcmOpOccId op-occ))
        (-> (json-response-status op-occ 202)
            (rur/header "Location" (op-occ-location request (:lcmOpOccId op-occ)))))
       (catch clojure.lang.ExceptionInfo e
         (log/error e "Failed to terminate app instance" app-instance-id)
         (exception-response e 400))
       (catch Exception e
         (log/error e "Unexpected error during termination" app-instance-id)
         (exception-response e 500)))))


 (defn operate-app-instance-handler
   "POST /app_lcm/v1/app_instances/{id}/operate - Start/Stop an app"
   [request]
   (let [app-instance-id (get-in request [:params :id])]
     (try
       (let [op-occ (submit-lifecycle-operation! request "OPERATE")]
         (log/info "Operate operation created:" (:lcmOpOccId op-occ))
        (-> (json-response-status op-occ 202)
            (rur/header "Location" (op-occ-location request (:lcmOpOccId op-occ)))))
       (catch clojure.lang.ExceptionInfo e
         (log/error e "Failed to operate app instance" app-instance-id)
         (exception-response e 400))
       (catch Exception e
         (log/error e "Unexpected error during operate" app-instance-id)
         (exception-response e 500)))))


 (defn list-app-lcm-op-occs-handler
   "GET /app_lcm/v1/app_lcm_op_occs - List all operation occurrences"
   [request]
   (try
     (let [params    (:params request)
           jobs      (query-resources request "job")
           resources (->> jobs
                          (filter mec-operation-job?)
                         (map #(job->northbound-op-occ % request))
                          vec)]
       (r/json-response (qf/process-query resources
                                          (merge params
                                                 {:base-uri (str (request-public-base-uri request) "/app_lcm_op_occs")}))))
     (catch clojure.lang.ExceptionInfo e
       (log/error e "Failed to list operation occurrences")
       (exception-response e 400))
     (catch Exception e
       (log/error e "Unexpected error listing operation occurrences")
       (exception-response e 500))))


 (defn get-app-lcm-op-occ-handler
   "GET /app_lcm/v1/app_lcm_op_occs/{id} - Get a specific operation occurrence"
   [request]
   (let [lcm-op-occ-id (get-in request [:params :id])]
     (try
        (let [job (crud/get-resource-throw-nok lcm-op-occ-id request)]
         (if (mec-operation-job? job)
          (r/json-response (job->northbound-op-occ job request))
           (json-response-status (not-found-error lcm-op-occ-id) 404)))
       (catch clojure.lang.ExceptionInfo e
         (log/error e "Failed to get operation occurrence" lcm-op-occ-id)
         (exception-response e 404))
       (catch Exception e
         (log/error e "Unexpected error getting operation occurrence" lcm-op-occ-id)
         (exception-response e 500)))))


(defn cancel-app-lcm-op-occ-handler
  "POST /app_lcm/v1/app_lcm_op_occs/{id}/cancel - Cancel an operation occurrence"
  [request]
  (let [lcm-op-occ-id (get-in request [:params :id])]
    (try
      (ensure-mec-operation-job! request lcm-op-occ-id)
      (validate-cancel-request! (:body request))
      (let [op-occ (update-operation-job! request lcm-op-occ-id {:state job-utils/state-canceled})]
        (json-response-status op-occ 202))
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to cancel operation occurrence" lcm-op-occ-id)
        (exception-response e 400))
      (catch Exception e
        (log/error e "Unexpected error canceling operation occurrence" lcm-op-occ-id)
        (exception-response e 500)))))


(defn fail-app-lcm-op-occ-handler
  "POST /app_lcm/v1/app_lcm_op_occs/{id}/fail - Force an operation occurrence to failed"
  [request]
  (let [lcm-op-occ-id (get-in request [:params :id])]
    (try
      (ensure-mec-operation-job! request lcm-op-occ-id)
      (let [op-occ (update-operation-job! request
                                          lcm-op-occ-id
                                          {:state          job-utils/state-failed
                                           :status-message "Operation failed by request"
                                           :return-code    1})]
        (json-response-status op-occ 200))
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to fail operation occurrence" lcm-op-occ-id)
        (exception-response e 400))
      (catch Exception e
        (log/error e "Unexpected error failing operation occurrence" lcm-op-occ-id)
        (exception-response e 500)))))


(defn retry-app-lcm-op-occ-handler
  "POST /app_lcm/v1/app_lcm_op_occs/{id}/retry - Retry an operation occurrence"
  [request]
  (let [lcm-op-occ-id (get-in request [:params :id])]
    (try
      (ensure-mec-operation-job! request lcm-op-occ-id)
      (let [op-occ (update-operation-job! request
                                          lcm-op-occ-id
                                          {:state          job-utils/state-queued
                                           :status-message "Operation retried"
                                           :return-code    nil})]
        (json-response-status op-occ 202))
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to retry operation occurrence" lcm-op-occ-id)
        (exception-response e 400))
      (catch Exception e
        (log/error e "Unexpected error retrying operation occurrence" lcm-op-occ-id)
        (exception-response e 500)))))


 (defn- subscription-authn
   [request]
   (or (:nuvla/authn request)
       (auth/current-authentication request)))


 (defn- subscription-owner
   [request]
   (or (auth/current-user-id request)
       (get-in request [:identity :user-id])
       "user/anonymous"))


 (defn- subscription-response
   [resource]
   (when resource
    (cond-> {:id               (some-> (:id resource) subscription/resource-id->api-id)
             :subscriptionType (some-> (:subscription-type resource)
                                       subscription/public-subscription-type)
              :callbackUri      (:callback-uri resource)
              :created          (:created resource)
              :updated          (:updated resource)
              :owner            (:owner resource)
              :active           (:active resource)}
       (:app-instance-filter resource)
       (assoc :appInstanceFilter
              (let [filter (:app-instance-filter resource)]
                (cond-> {}
                  (:app-instance-id filter) (assoc :appInstanceId (:app-instance-id filter))
                  (:app-name filter) (assoc :appName (:app-name filter))
                  (:operational-state filter) (assoc :operationalState (:operational-state filter))
                  (:instantiation-state filter) (assoc :instantiationState (:instantiation-state filter)))))
       (:app-lcm-op-occ-filter resource)
       (assoc :appLcmOpOccFilter
              (let [filter (:app-lcm-op-occ-filter resource)]
                (cond-> {}
                  (:app-instance-id filter) (assoc :appInstanceId (:app-instance-id filter))
                  (:operation-type filter) (assoc :operationType (:operation-type filter))
                  (:operation-state filter) (assoc :operationState (:operation-state filter))))))))


 (defn- normalize-app-instance-filter
   [filter-opts]
   (cond-> {}
     (or (:appInstanceId filter-opts) (:app-instance-id filter-opts))
     (assoc :app-instance-id (or (:appInstanceId filter-opts)
                                 (:app-instance-id filter-opts)))
     (or (:appName filter-opts) (:app-name filter-opts))
     (assoc :app-name (or (:appName filter-opts)
                          (:app-name filter-opts)))
     (or (:operationalState filter-opts) (:operational-state filter-opts))
     (assoc :operational-state (or (:operationalState filter-opts)
                                   (:operational-state filter-opts)))
     (or (:instantiationState filter-opts) (:instantiation-state filter-opts))
     (assoc :instantiation-state (or (:instantiationState filter-opts)
                                     (:instantiation-state filter-opts)))))


 (defn- normalize-app-lcm-op-occ-filter
   [filter-opts]
   (cond-> {}
     (or (:appInstanceId filter-opts) (:app-instance-id filter-opts))
     (assoc :app-instance-id (or (:appInstanceId filter-opts)
                                 (:app-instance-id filter-opts)))
     (or (:operationType filter-opts) (:operation-type filter-opts))
     (assoc :operation-type (or (:operationType filter-opts)
                                (:operation-type filter-opts)))
     (or (:operationState filter-opts) (:operation-state filter-opts))
     (assoc :operation-state (or (:operationState filter-opts)
                                 (:operation-state filter-opts)))))


 (defn- retrieve-subscription-resource
   [public-id]
   (some-> public-id
           subscription/api-id->resource-id
           crud/retrieve-by-id-as-admin1))


 (defn query-user-subscriptions
   [request]
   (-> {:params      {:resource-name subscription/resource-type}
        :cimi-params {:last 1000}
        :nuvla/authn (subscription-authn request)}
       crud/query
       :body
       :resources))


 (defn- normalize-callback-uri
   [callback-uri]
   (some-> callback-uri str str/trim))


 (defn- canonicalize-filter
   [filter-opts]
   (some->> filter-opts
            (remove (comp nil? val))
            (into {})))


 (defn- duplicate-active-subscription
   [request subscription-type callback-uri filter-opts user-id]
  (let [subscription-type* (subscription/canonical-subscription-type subscription-type)
        filter-key (subscription/subscription-type->filter-key subscription-type*)
         callback-uri* (normalize-callback-uri callback-uri)
         filter-opts*  (canonicalize-filter filter-opts)]
     (some (fn [existing]
             (and (:active existing)
                  (= user-id (:owner existing))
                 (= subscription-type*
                    (subscription/canonical-subscription-type (:subscription-type existing)))
                  (= callback-uri* (normalize-callback-uri (:callback-uri existing)))
                  (= filter-opts* (canonicalize-filter (get existing filter-key)))))
           (query-user-subscriptions request))))


 (defn create-subscription-handler
   "POST /app_lcm/v1/subscriptions - Create a new subscription"
   [request]
   (try
     (let [body              (:body request)
          subscription-type (subscription/canonical-subscription-type (:subscriptionType body))
           callback-uri      (:callbackUri body)
          filter-opts       (case subscription-type
                              "AppInstanceStateChange"
                               (normalize-app-instance-filter (:appInstanceFilter body))
                              "AppLcmOpOccStateChange"
                               (normalize-app-lcm-op-occ-filter (:appLcmOpOccFilter body))
                               {})
           user-id           (subscription-owner request)]
      (when-not (contains? subscription/accepted-subscription-types subscription-type)
         (throw (ex-info "Invalid subscription type"
                         {:status 400
                          :subscription-type subscription-type})))
       (when (duplicate-active-subscription request subscription-type callback-uri filter-opts user-id)
         (throw (ex-info "An active MEC subscription with the same callback URI and filter already exists"
                         {:status 409})))
      (let [filter-key (subscription/subscription-type->filter-key subscription-type)
             preview    (subscription/create-subscription
                          subscription-type
                          (normalize-callback-uri callback-uri)
                          filter-opts
                          user-id)
             sub        (cond-> {:subscription-type subscription-type
                                 :callback-uri      (normalize-callback-uri callback-uri)
                                 :owner             user-id
                                 :active            true}
                          (and filter-key (seq filter-opts)) (assoc filter-key filter-opts))]
         (let [validation (subscription/validate-subscription preview)]
           (when-not (:valid? validation)
             (throw (ex-info "Invalid subscription"
                             {:status 400
                              :errors (:errors validation)}))))
         (let [create-response (crud/add {:params      {:resource-name subscription/resource-type}
                                          :body        sub
                                          :nuvla/authn (subscription-authn request)})
               resource-id     (get-in create-response [:body :resource-id])
               persisted       (crud/retrieve-by-id-as-admin1 resource-id)]
           (log/info "Created subscription" resource-id "for user" user-id)
           (json-response-status (subscription-response persisted) 201))))
     (catch clojure.lang.ExceptionInfo e
       (let [data (ex-data e)]
         (log/error e "Failed to create subscription")
         (json-response-status
           ((case (or (:status data) 400)
              409 conflict-error
              validation-error)
            (ex-message e))
           (or (:status data) 400))))
     (catch Exception e
       (log/error e "Unexpected error creating subscription")
       (json-response-status (problem-details
                               "about:blank"
                               "Internal Server Error"
                               500
                               :detail (ex-message e)) 500))))


 (defn list-subscriptions-handler
   "GET /app_lcm/v1/subscriptions - List subscriptions"
   [request]
   (try
     (let [query-params     (:params request)
          active-user-subs (->> (query-user-subscriptions request)
                                 (filter :active)
                                 (mapv subscription-response))]
       (r/json-response (qf/process-query active-user-subs
                                          (merge query-params
                                                 {:base-uri (str (request-public-base-uri request) "/subscriptions")}))))
     (catch Exception e
       (log/error e "Failed to list subscriptions")
       (json-response-status (problem-details
                               "about:blank"
                               "Internal Server Error"
                               500
                               :detail (ex-message e)) 500))))


 (defn get-subscription-handler
   "GET /app_lcm/v1/subscriptions/{id} - Get a specific subscription"
   [request]
   (try
     (let [subscription-id (get-in request [:params :id])
           user-id         (subscription-owner request)
           sub             (retrieve-subscription-resource subscription-id)]
       (cond
         (or (nil? sub) (false? (:active sub)))
         (json-response-status (not-found-error subscription-id) 404)

         (not= (:owner sub) user-id)
         (json-response-status (problem-details
                                 "https://docs.nuvla.io/mec/errors/forbidden"
                                 "Access Forbidden"
                                 403
                                 :detail "You do not have permission to access this subscription"
                                 :instance subscription-id) 403)

         :else
         (r/json-response (subscription-response sub))))
     (catch Exception e
       (log/error e "Failed to get subscription")
       (json-response-status (problem-details
                               "about:blank"
                               "Internal Server Error"
                               500
                               :detail (ex-message e)) 500))))


 (defn delete-subscription-handler
   "DELETE /app_lcm/v1/subscriptions/{id} - Delete a subscription"
   [request]
   (try
     (let [subscription-id (get-in request [:params :id])
           user-id         (subscription-owner request)
           sub             (retrieve-subscription-resource subscription-id)]
       (cond
         (or (nil? sub) (false? (:active sub)))
         (json-response-status (not-found-error subscription-id) 404)

         (not= (:owner sub) user-id)
         (json-response-status (problem-details
                                 "https://docs.nuvla.io/mec/errors/forbidden"
                                 "Access Forbidden"
                                 403
                                 :detail "You do not have permission to delete this subscription"
                                 :instance subscription-id) 403)

         :else
         (do
           (let [resource-id (subscription/api-id->resource-id subscription-id)]
             (crud/delete {:params      (u/id->request-params resource-id)
                           :nuvla/authn (subscription-authn request)})
             (log/info "Deleted subscription" resource-id))
           (json-response-status nil 204))))
     (catch Exception e
       (log/error e "Failed to delete subscription")
       (json-response-status (problem-details
                               "about:blank"
                               "Internal Server Error"
                               500
                               :detail (ex-message e)) 500))))


 (def routes
   "MEC 010-2 API routes"
   [[(str "/" base-uri "/app_instances")
     {:get  {:handler list-app-instances-handler
             :summary "List app instances"}
      :post {:handler create-app-instance-handler
             :summary "Create app instance"}}]

    [(str "/" base-uri "/app_instances/:id")
     {:get    {:handler get-app-instance-handler
               :summary "Get app instance"}
      :delete {:handler delete-app-instance-handler
               :summary "Delete app instance"}}]

    [(str "/" base-uri "/app_instances/:id/instantiate")
     {:post {:handler instantiate-app-instance-handler
             :summary "Instantiate app instance"}}]

    [(str "/" base-uri "/app_instances/:id/terminate")
     {:post {:handler terminate-app-instance-handler
             :summary "Terminate app instance"}}]

    [(str "/" base-uri "/app_instances/:id/operate")
     {:post {:handler operate-app-instance-handler
             :summary "Operate app instance (start/stop)"}}]

    [(str "/" base-uri "/app_lcm_op_occs")
     {:get {:handler list-app-lcm-op-occs-handler
            :summary "List operation occurrences"}}]

    [(str "/" base-uri "/app_lcm_op_occs/:id")
     {:get {:handler get-app-lcm-op-occ-handler
            :summary "Get operation occurrence"}}]

    [(str "/" base-uri "/app_lcm_op_occs/:id/cancel")
     {:post {:handler cancel-app-lcm-op-occ-handler
             :summary "Cancel operation occurrence"}}]

    [(str "/" base-uri "/app_lcm_op_occs/:id/fail")
     {:post {:handler fail-app-lcm-op-occ-handler
             :summary "Fail operation occurrence"}}]

    [(str "/" base-uri "/app_lcm_op_occs/:id/retry")
     {:post {:handler retry-app-lcm-op-occ-handler
             :summary "Retry operation occurrence"}}]

    [(str "/" base-uri "/subscriptions")
     {:get  {:handler list-subscriptions-handler
             :summary "List subscriptions"}
      :post {:handler create-subscription-handler
             :summary "Create subscription"}}]

    [(str "/" base-uri "/subscriptions/:id")
     {:get    {:handler get-subscription-handler
               :summary "Get subscription"}
      :delete {:handler delete-subscription-handler
               :summary "Delete subscription"}}]])


 (defn initialize
   "Initialize MEC 010-2 API"
   []
   (log/info "Initializing MEC 010-2 Application Lifecycle Management API")
   (log/info "API version:" api-version)
   (log/info "Base URI:" base-uri))
