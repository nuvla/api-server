(ns com.sixsq.nuvla.server.resources.mec.app-lcm-v2
  "MEC 010-2 Application Lifecycle Management API v2
   
   Provides RESTful endpoints compliant with ETSI GS MEC 010-2 v2.2.1
   for application lifecycle management at the MEO level.
   
   Endpoints:
   - POST   /app_lcm/v2/app_instances          - Create app instance
   - GET    /app_lcm/v2/app_instances          - List app instances
   - GET    /app_lcm/v2/app_instances/{id}     - Get app instance
   - DELETE /app_lcm/v2/app_instances/{id}     - Delete app instance
   - POST   /app_lcm/v2/app_instances/{id}/instantiate - Instantiate
   - POST   /app_lcm/v2/app_instances/{id}/terminate   - Terminate
   - POST   /app_lcm/v2/app_instances/{id}/operate     - Start/Stop
   - GET    /app_lcm/v2/app_lcm_op_occs        - List operations
   - GET    /app_lcm/v2/app_lcm_op_occs/{id}   - Get operation
   - POST   /app_lcm/v2/subscriptions          - Create subscription
   - GET    /app_lcm/v2/subscriptions          - List subscriptions
   - GET    /app_lcm/v2/subscriptions/{id}     - Get subscription
   - DELETE /app_lcm/v2/subscriptions/{id}     - Delete subscription
   
   Standard: ETSI GS MEC 010-2 v2.2.1"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
    [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm3]
    [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]
    [com.sixsq.nuvla.server.resources.mec.query-filter :as qf]
    [com.sixsq.nuvla.server.util.response :as r]))


;;
;; Resource Type Definition
;;

(def ^:const resource-type "mec-app-lcm")
(def ^:const collection-type "mec-app-lcm-collection")
(def ^:const api-version "v2")
(def ^:const base-uri (str "app_lcm/" api-version))


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


(def ^:private max-query-results
  1000)


(def ^:private mec-operation-types
  #{"INSTANTIATE" "TERMINATE" "OPERATE"})


(def ^:private eligible-mepm-statuses
  #{"ONLINE" "DEGRADED"})


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


(defn- retrieve-app-instance-deployment
  [request app-instance-id]
  (crud/get-resource-throw-nok app-instance-id request))


(defn- retrieve-app-instance-info
  [request app-instance-id]
  (->> app-instance-id
       (retrieve-app-instance-deployment request)
       app-instance/deployment->app-instance-info))


(defn- request-change-state->target-state
  [change-state]
  (cond
    (keyword? change-state) (-> change-state name str/upper-case)
    (string? change-state) (str/upper-case change-state)
    :else nil))


(defn- validate-instantiate-request!
  [request app-instance-id]
  (let [{:keys [instantiationState]} (retrieve-app-instance-info request app-instance-id)]
    (when-not (= "NOT_INSTANTIATED" instantiationState)
      (throw-status 409 "App instance must be NOT_INSTANTIATED before instantiation"
                    {:app-instance-id app-instance-id
                     :instantiation-state instantiationState}))))


(defn- validate-terminate-request!
  [request app-instance-id]
  (let [{:keys [instantiationState]} (retrieve-app-instance-info request app-instance-id)]
    (when (= "NOT_INSTANTIATED" instantiationState)
      (throw-status 409 "App instance must be INSTANTIATED before termination"
                    {:app-instance-id app-instance-id
                     :instantiation-state instantiationState}))))


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
  [request app-instance-id action body]
  (cond-> {:params      {:resource-name "deployment"
                         :uuid          (u/id->uuid app-instance-id)
                         :action        action}
           :nuvla/authn (:nuvla/authn request)}
    (seq body) (assoc :body body)))


(defn- throw-on-unsuccessful-mm3-response!
  [response endpoint operation]
  (when-not (:success? response)
    (throw-status 503
                  (str "MEPM " operation " failed: " (:message response))
                  {:mepm-endpoint endpoint
                   :operation operation
                   :error (:error response)}))
  response)


(defn- resolve-mepm-for-deployment!
  [deployment]
  (let [nuvlabox-id (:nuvlabox deployment)
        eligible?   #(contains? eligible-mepm-statuses (:status %))
        mepms       (if nuvlabox-id
                      (query-resources-as-admin "mepm" (str "mec-host-id='" nuvlabox-id "'"))
                      (query-resources-as-admin "mepm"))
        eligible    (filter eligible? mepms)]
    (cond
      (and nuvlabox-id (= 1 (count eligible))) (first eligible)
      (and nuvlabox-id (empty? eligible))
      (throw-status 503
                    (str "No eligible MEPM found for target NuvlaBox " nuvlabox-id)
                    {:nuvlabox-id nuvlabox-id})
      (and nuvlabox-id (> (count eligible) 1))
      (throw-status 409
                    (str "Multiple eligible MEPMs found for target NuvlaBox " nuvlabox-id)
                    {:nuvlabox-id nuvlabox-id})
      (= 1 (count eligible)) (first eligible)
      (empty? eligible) (throw-status 503 "No eligible MEPM available")
      :else (throw-status 409 "Multiple eligible MEPMs available; explicit host targeting is required"))))


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


(defn- tag-mec-operation-job!
  [job-id app-instance-id operation-type request-params metadata]
  (crud/edit-by-id-as-admin job-id (merge {:mec-operation-type  operation-type
                                           :mec-app-instance-id app-instance-id
                                           :mec-request-params  request-params}
                                          metadata))
  (crud/retrieve-by-id-as-admin job-id))


(defn- run-mec-lifecycle-action!
  [request app-instance-id action operation-type request-params metadata]
  (let [response (crud/do-action (deployment-action-request request app-instance-id action request-params))
        status   (:status response)]
    (when-not (= 202 status)
      (throw-status (or status 500)
                    (or (get-in response [:body :detail])
                        (get-in response [:body :message])
                        "Lifecycle action failed")
                    {:app-instance-id app-instance-id
                     :action action}))
    (let [job-id (action-response->job-id response)]
      (when-not job-id
        (throw-status 500 "Lifecycle action did not return a job identifier"
                      {:app-instance-id app-instance-id
                       :action action}))
      (let [op-occ (-> (tag-mec-operation-job! job-id app-instance-id operation-type request-params metadata)
                       app-lcm-op-occ/job->app-lcm-op-occ)]
        (dispatcher/dispatch-app-lcm-op-occ-state-change! op-occ "OPERATION_STATE" nil)
        op-occ))))


;;
;; App Instance Endpoints
;;

(defn create-app-instance-handler
  "POST /app_lcm/v2/app_instances - Create a new app instance"
  [request]
  (try
    (let [body             (:body request)
          _                (app-instance/validate-create-app-instance-request body)
          deployment-body  (app-instance/create-request->deployment body)
          create-response  (crud/add (assoc request
                                        :params {:resource-name "deployment"}
                                        :body deployment-body))
          deployment-id    (get-in create-response [:body :resource-id])
          deployment       (crud/get-resource-throw-nok deployment-id request)
          app-instance-info (app-instance/deployment->app-instance-info deployment)]
      (dispatcher/dispatch-app-instance-state-change! app-instance-info "INSTANTIATION_STATE" nil)
      (json-response-status app-instance-info 201))
    (catch clojure.lang.ExceptionInfo e
      (log/error e "Failed to create app instance")
      (exception-response e 400))
    (catch Exception e
      (log/error e "Unexpected error creating app instance")
      (exception-response e 500))))


(defn list-app-instances-handler
  "GET /app_lcm/v2/app_instances - List all app instances
   
   Supports MEC 010-2 query parameters:
   - filter:  FIQL-like filter expression (e.g., (eq,appName,my-app))
   - page:    Page number (1-based, default 1)
   - size:    Page size (default 20, max 100)
   - fields:  Comma-separated field names for field selection"
  [request]
  (try
    (let [params      (:params request)
          deployments (query-resources request "deployment")
          resources   (mapv app-instance/deployment->app-instance-info deployments)]
      (r/json-response (qf/process-query resources
                                         (merge params
                                                {:base-uri (str "/" base-uri "/app_instances")}))))
    (catch clojure.lang.ExceptionInfo e
      (log/error e "Failed to list app instances")
      (exception-response e 400))
    (catch Exception e
      (log/error e "Unexpected error listing app instances")
      (exception-response e 500))))


(defn get-app-instance-handler
  "GET /app_lcm/v2/app_instances/{id} - Get a specific app instance"
  [request]
  (let [app-instance-id (get-in request [:params :id])]
    (try
      (-> app-instance-id
          (crud/get-resource-throw-nok request)
          app-instance/deployment->app-instance-info
          r/json-response)
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to get app instance" app-instance-id)
        (exception-response e 404))
      (catch Exception e
        (log/error e "Unexpected error getting app instance" app-instance-id)
        (exception-response e 500)))))


(defn delete-app-instance-handler
  "DELETE /app_lcm/v2/app_instances/{id} - Delete an app instance"
  [request]
  (let [app-instance-id (get-in request [:params :id])]
    (try
      (let [deployment         (crud/get-resource-throw-nok app-instance-id request)
            app-instance-info  (app-instance/deployment->app-instance-info deployment)
            inst-state         (:instantiationState app-instance-info)
            [_ uuid]           (u/parse-id app-instance-id)]
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


;;
;; Lifecycle Operation Endpoints
;;

(defn instantiate-app-instance-handler
  "POST /app_lcm/v2/app_instances/{id}/instantiate - Instantiate an app"
  [request]
  (let [app-instance-id (get-in request [:params :id])
        body            (:body request)]
    (try
      (log/info "Instantiate request for" app-instance-id)

      (validate-instantiate-request! request app-instance-id)
      (let [mepm   (resolve-mepm-for-app-instance! request app-instance-id)
            op-occ (run-mec-lifecycle-action! request
                                             app-instance-id
                                             "start"
                                             "INSTANTIATE"
                                             body
                                             {:mepm-id       (:id mepm)
                                              :mepm-endpoint (:endpoint mepm)
                                              :mec-host-id   (:mec-host-id mepm)})]
        (log/info "Instantiation operation created:" (:lcmOpOccId op-occ))
        (json-response-status op-occ 202))

      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to instantiate app instance" app-instance-id)
        (exception-response e 400))
      (catch Exception e
        (log/error e "Unexpected error during instantiation" app-instance-id)
        (exception-response e 500)))))


(defn terminate-app-instance-handler
  "POST /app_lcm/v2/app_instances/{id}/terminate - Terminate an app"
  [request]
  (let [app-instance-id (get-in request [:params :id])
        body            (:body request)]
    (try
      (log/info "Terminate request for" app-instance-id)

      (validate-terminate-request! request app-instance-id)
      (let [mepm   (resolve-mepm-for-app-instance! request app-instance-id)
            op-occ (run-mec-lifecycle-action! request
                                             app-instance-id
                                             "stop"
                                             "TERMINATE"
                                             body
                                             {:mepm-id       (:id mepm)
                                              :mepm-endpoint (:endpoint mepm)
                                              :mec-host-id   (:mec-host-id mepm)})]
        (log/info "Termination operation created:" (:lcmOpOccId op-occ))
        (json-response-status op-occ 202))

      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to terminate app instance" app-instance-id)
        (exception-response e 400))
      (catch Exception e
        (log/error e "Unexpected error during termination" app-instance-id)
        (exception-response e 500)))))


(defn operate-app-instance-handler
  "POST /app_lcm/v2/app_instances/{id}/operate - Start/Stop an app"
  [request]
  (let [app-instance-id (get-in request [:params :id])
        body            (:body request)]
    (try
      (let [body         (validate-operate-request! request app-instance-id body)
            mepm         (resolve-mepm-for-app-instance! request app-instance-id)
            target-state (:changeStateTo body)
            action       (if (= "STARTED" target-state) "start" "stop")]
        (log/info "Operate request for" app-instance-id "changeStateTo" target-state)
        (let [op-occ (run-mec-lifecycle-action! request
                                               app-instance-id
                                               action
                                               "OPERATE"
                                               body
                                               {:mepm-id       (:id mepm)
                                                :mepm-endpoint (:endpoint mepm)
                                                :mec-host-id   (:mec-host-id mepm)})]
          (log/info "Operate operation created:" (:lcmOpOccId op-occ))
          (json-response-status op-occ 202)))

      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to operate app instance" app-instance-id)
        (exception-response e 400))
      (catch Exception e
        (log/error e "Unexpected error during operate" app-instance-id)
        (exception-response e 500)))))


;;
;; Operation Occurrence Endpoints
;;

(defn list-app-lcm-op-occs-handler
  "GET /app_lcm/v2/app_lcm_op_occs - List all operation occurrences
   
   Supports MEC 010-2 query parameters:
   - filter:  FIQL-like filter expression (e.g., (eq,operationType,INSTANTIATE))
   - page:    Page number (1-based, default 1)
   - size:    Page size (default 20, max 100)
   - fields:  Comma-separated field names for field selection"
  [request]
  (try
    (let [params     (:params request)
          jobs       (query-resources request "job")
          resources  (->> jobs
                          (filter mec-operation-job?)
                          (map app-lcm-op-occ/job->app-lcm-op-occ)
                          vec)]
      (r/json-response (qf/process-query resources
                                         (merge params
                                                {:base-uri (str "/" base-uri "/app_lcm_op_occs")}))))
    (catch clojure.lang.ExceptionInfo e
      (log/error e "Failed to list operation occurrences")
      (exception-response e 400))
    (catch Exception e
      (log/error e "Unexpected error listing operation occurrences")
      (exception-response e 500))))


(defn get-app-lcm-op-occ-handler
  "GET /app_lcm/v2/app_lcm_op_occs/{id} - Get a specific operation occurrence"
  [request]
  (let [lcm-op-occ-id (get-in request [:params :id])]
    (try
      (let [job (crud/get-resource-throw-nok lcm-op-occ-id request)]
        (if (mec-operation-job? job)
          (r/json-response (app-lcm-op-occ/job->app-lcm-op-occ job))
          (json-response-status (not-found-error lcm-op-occ-id) 404)))
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to get operation occurrence" lcm-op-occ-id)
        (exception-response e 404))
      (catch Exception e
        (log/error e "Unexpected error getting operation occurrence" lcm-op-occ-id)
        (exception-response e 500)))))


;;
;; Subscription Endpoints
;;

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
    (-> (select-keys resource [:id
                               :subscription-type
                               :callback-uri
                               :app-instance-filter
                               :app-lcm-op-occ-filter
                               :created
                               :updated
                               :owner
                               :active])
        (update :id subscription/resource-id->api-id))))

(defn- retrieve-subscription-resource
  [public-id]
  (some-> public-id
          subscription/api-id->resource-id
          crud/retrieve-by-id-as-admin1))

(defn- query-user-subscriptions
  [request]
  (-> {:params      {:resource-name subscription/resource-type}
       :cimi-params {:last 1000}
       :nuvla/authn (subscription-authn request)}
      crud/query
      :body
      :resources))

(defn create-subscription-handler
  "POST /app_lcm/v2/subscriptions - Create a new subscription"
  [request]
  (try
    (let [body              (:body request)
          subscription-type (:subscriptionType body)
          callback-uri      (:callbackUri body)
          filter-opts       (or (:appInstanceFilter body)
                                (:appLcmOpOccFilter body)
                                {})
          user-id           (subscription-owner request)]
      
      ;; Validate subscription type
      (when-not (contains? subscription/subscription-types subscription-type)
        (throw (ex-info "Invalid subscription type"
                        {:status 400
                         :subscription-type subscription-type})))
      
      ;; Create subscription body
      (let [filter-key (case subscription-type
                         "AppInstanceStateChangeNotification" :app-instance-filter
                         "AppLcmOpOccStateChangeNotification" :app-lcm-op-occ-filter
                         nil)
            preview (subscription/create-subscription
                      subscription-type
                      callback-uri
                      filter-opts
                      user-id)
            sub (cond-> {:subscription-type subscription-type
                         :callback-uri      callback-uri
                         :owner             user-id
                         :active            true}
                  (and filter-key (seq filter-opts)) (assoc filter-key filter-opts))]
        
        ;; Validate subscription
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
        (json-response-status (validation-error (ex-message e))
                              (or (:status data) 400))))
    (catch Exception e
      (log/error e "Unexpected error creating subscription")
      (json-response-status (problem-details
                              "about:blank"
                              "Internal Server Error"
                              500
                              :detail (ex-message e)) 500))))


(defn list-subscriptions-handler
  "GET /app_lcm/v2/subscriptions - List subscriptions
   
   Supports MEC 010-2 query parameters:
   - filter:  FIQL-like filter expression (e.g., (eq,subscriptionType,AppInstanceStateChangeNotification))
   - page:    Page number (1-based, default 1)
   - size:    Page size (default 20, max 100)
   - fields:  Comma-separated field names for field selection
   
   Also supports legacy parameters for backward compatibility:
   - subscriptionType: Filter by subscription type (deprecated, use filter parameter instead)
   - limit/offset: Pagination (deprecated, use page/size instead)"
  [request]
  (try
    (let [query-params      (:params request)
          active-user-subs  (->> (query-user-subscriptions request)
                                 (filter :active)
                                 (mapv subscription-response))]
      
      ;; Apply query processing (filter, pagination, field selection)
      (r/json-response (qf/process-query active-user-subs
                                         (merge query-params
                                                {:base-uri (str "/" base-uri "/subscriptions")}))))
    
    (catch Exception e
      (log/error e "Failed to list subscriptions")
      (json-response-status (problem-details
                              "about:blank"
                              "Internal Server Error"
                              500
                              :detail (ex-message e)) 500))))


(defn get-subscription-handler
  "GET /app_lcm/v2/subscriptions/{id} - Get a specific subscription"
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
  "DELETE /app_lcm/v2/subscriptions/{id} - Delete a subscription"
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


;;
;; Route Definitions
;;

(def routes
  "MEC 010-2 API routes"
  [;; App Instance Collection
   [(str "/" base-uri "/app_instances")
    {:get  {:handler list-app-instances-handler
            :summary "List app instances"}
     :post {:handler create-app-instance-handler
            :summary "Create app instance"}}]
   
   ;; App Instance Item
   [(str "/" base-uri "/app_instances/:id")
    {:get    {:handler get-app-instance-handler
              :summary "Get app instance"}
     :delete {:handler delete-app-instance-handler
              :summary "Delete app instance"}}]
   
   ;; App Instance Lifecycle Operations
   [(str "/" base-uri "/app_instances/:id/instantiate")
    {:post {:handler instantiate-app-instance-handler
            :summary "Instantiate app instance"}}]
   
   [(str "/" base-uri "/app_instances/:id/terminate")
    {:post {:handler terminate-app-instance-handler
            :summary "Terminate app instance"}}]
   
   [(str "/" base-uri "/app_instances/:id/operate")
    {:post {:handler operate-app-instance-handler
            :summary "Operate app instance (start/stop)"}}]
   
   ;; Operation Occurrence Collection
   [(str "/" base-uri "/app_lcm_op_occs")
    {:get {:handler list-app-lcm-op-occs-handler
           :summary "List operation occurrences"}}]
   
   ;; Operation Occurrence Item
   [(str "/" base-uri "/app_lcm_op_occs/:id")
    {:get {:handler get-app-lcm-op-occ-handler
           :summary "Get operation occurrence"}}]
   
   ;; Subscription Collection
   [(str "/" base-uri "/subscriptions")
    {:get  {:handler list-subscriptions-handler
            :summary "List subscriptions"}
     :post {:handler create-subscription-handler
            :summary "Create subscription"}}]
   
   ;; Subscription Item
   [(str "/" base-uri "/subscriptions/:id")
    {:get    {:handler get-subscription-handler
              :summary "Get subscription"}
     :delete {:handler delete-subscription-handler
              :summary "Delete subscription"}}]])


;;
;; Initialization
;;

(defn initialize
  "Initialize MEC 010-2 API"
  []
  (log/info "Initializing MEC 010-2 Application Lifecycle Management API")
  (log/info "API version:" api-version)
  (log/info "Base URI:" base-uri))
