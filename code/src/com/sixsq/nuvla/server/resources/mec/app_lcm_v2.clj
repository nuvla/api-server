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
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
    [com.sixsq.nuvla.server.resources.mec.lifecycle-handler :as lifecycle]
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


;;
;; App Instance Endpoints
;;

(defn create-app-instance-handler
  "POST /app_lcm/v2/app_instances - Create a new app instance"
  [request]
  (try
    (let [body     (:body request)
          _        (app-instance/validate-app-instance-info body)]
      ;; TODO: Integrate with deployment CRUD create
      (r/json-response {:message "App instance creation pending integration"
                        :appInstanceInfo body} 501))
    (catch Exception e
      (log/error e "Failed to create app instance")
      (r/json-response (validation-error (ex-message e)) 400))))


(defn list-app-instances-handler
  "GET /app_lcm/v2/app_instances - List all app instances
   
   Supports MEC 010-2 query parameters:
   - filter:  FIQL-like filter expression (e.g., (eq,appName,my-app))
   - page:    Page number (1-based, default 1)
   - size:    Page size (default 20, max 100)
   - fields:  Comma-separated field names for field selection"
  [request]
  (try
    (let [params   (:params request)
          ;; TODO: Replace with actual deployment CRUD query
          ;; For now, return empty collection with query processing
          resources []]
      (r/json-response (qf/process-query resources
                                         (merge params
                                                {:base-uri (str "/" base-uri "/app_instances")}))))
    (catch Exception e
      (log/error e "Failed to list app instances")
      (r/json-response (validation-error (ex-message e)) 400))))


(defn get-app-instance-handler
  "GET /app_lcm/v2/app_instances/{id} - Get a specific app instance"
  [request]
  (let [app-instance-id (get-in request [:params :id])]
    ;; TODO: Integrate with deployment CRUD read
    (r/json-response (not-found-error app-instance-id) 404)))


(defn delete-app-instance-handler
  "DELETE /app_lcm/v2/app_instances/{id} - Delete an app instance"
  [request]
  (let [app-instance-id (get-in request [:params :id])]
    (try
      ;; TODO: Integrate with deployment CRUD delete
      (r/json-response {:message "App instance deletion pending integration"} 501)
      (catch Exception e
        (log/error e "Failed to delete app instance" app-instance-id)
        (r/json-response (not-found-error app-instance-id) 404)))))


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
      
      ;; Execute instantiation via lifecycle handler
      (let [op-occ (lifecycle/instantiate app-instance-id body)]
        (log/info "Instantiation operation created:" (:lcmOpOccId op-occ))
        
        ;; Return operation occurrence with 202 Accepted
        (r/json-response op-occ 202))
      
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to instantiate app instance" app-instance-id)
        (r/json-response (validation-error (ex-message e)) 400))
      (catch Exception e
        (log/error e "Unexpected error during instantiation" app-instance-id)
        (r/json-response (problem-details
                           "about:blank"
                           "Internal Server Error"
                           500
                           :detail (ex-message e)
                           :instance app-instance-id) 500)))))


(defn terminate-app-instance-handler
  "POST /app_lcm/v2/app_instances/{id}/terminate - Terminate an app"
  [request]
  (let [app-instance-id (get-in request [:params :id])
        body            (:body request)]
    (try
      (log/info "Terminate request for" app-instance-id)
      
      ;; Execute termination via lifecycle handler
      (let [op-occ (lifecycle/terminate app-instance-id body)]
        (log/info "Termination operation created:" (:lcmOpOccId op-occ))
        
        ;; Return operation occurrence with 202 Accepted
        (r/json-response op-occ 202))
      
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to terminate app instance" app-instance-id)
        (r/json-response (validation-error (ex-message e)) 400))
      (catch Exception e
        (log/error e "Unexpected error during termination" app-instance-id)
        (r/json-response (problem-details
                           "about:blank"
                           "Internal Server Error"
                           500
                           :detail (ex-message e)
                           :instance app-instance-id) 500)))))


(defn operate-app-instance-handler
  "POST /app_lcm/v2/app_instances/{id}/operate - Start/Stop an app"
  [request]
  (let [app-instance-id (get-in request [:params :id])
        body            (:body request)]
    (try
      (log/info "Operate request for" app-instance-id "changeStateTo" (:changeStateTo body))
      
      ;; Execute operate via lifecycle handler
      (let [op-occ (lifecycle/operate app-instance-id body)]
        (log/info "Operate operation created:" (:lcmOpOccId op-occ))
        
        ;; Return operation occurrence with 202 Accepted
        (r/json-response op-occ 202))
      
      (catch clojure.lang.ExceptionInfo e
        (log/error e "Failed to operate app instance" app-instance-id)
        (r/json-response (validation-error (ex-message e)) 400))
      (catch Exception e
        (log/error e "Unexpected error during operate" app-instance-id)
        (r/json-response (problem-details
                           "about:blank"
                           "Internal Server Error"
                           500
                           :detail (ex-message e)
                           :instance app-instance-id) 500)))))


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
    (let [params   (:params request)
          ;; TODO: Replace with actual job CRUD query
          ;; For now, return empty collection with query processing
          resources []]
      (r/json-response (qf/process-query resources
                                         (merge params
                                                {:base-uri (str "/" base-uri "/app_lcm_op_occs")}))))
    (catch Exception e
      (log/error e "Failed to list operation occurrences")
      (r/json-response (validation-error (ex-message e)) 400))))


(defn get-app-lcm-op-occ-handler
  "GET /app_lcm/v2/app_lcm_op_occs/{id} - Get a specific operation occurrence"
  [request]
  (let [lcm-op-occ-id (get-in request [:params :id])]
    ;; TODO: Integrate with job CRUD read
    (r/json-response (not-found-error lcm-op-occ-id) 404)))


;;
;; Subscription Endpoints
;;

;; In-memory subscription store (TODO: Replace with persistent storage)
(def subscription-store (atom []))

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
          user-id           (or (get-in request [:identity :user-id])
                                "user/anonymous")]
      
      ;; Validate subscription type
      (when-not (contains? subscription/subscription-types subscription-type)
        (throw (ex-info "Invalid subscription type"
                        {:status 400
                         :subscription-type subscription-type})))
      
      ;; Create subscription
      (let [sub (subscription/create-subscription
                  subscription-type
                  callback-uri
                  filter-opts
                  user-id)]
        
        ;; Validate subscription
        (let [validation (subscription/validate-subscription sub)]
          (when-not (:valid? validation)
            (throw (ex-info "Invalid subscription"
                            {:status 400
                             :errors (:errors validation)}))))
        
        ;; Store subscription
        (swap! subscription-store conj sub)
        
        (log/info "Created subscription" (:id sub) "for user" user-id)
        (r/json-response sub 201)))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/error e "Failed to create subscription")
        (r/json-response (validation-error (ex-message e))
                         (or (:status data) 400))))
    (catch Exception e
      (log/error e "Unexpected error creating subscription")
      (r/json-response (problem-details
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
          user-id           (get-in request [:identity :user-id])
          
          ;; Filter subscriptions by user and active status
          active-user-subs  (filter (fn [s]
                                      (and (:active s)
                                           (or (nil? user-id)
                                               (= (:owner s) user-id))))
                                    @subscription-store)]
      
      ;; Apply query processing (filter, pagination, field selection)
      (r/json-response (qf/process-query (vec active-user-subs)
                                         (merge query-params
                                                {:base-uri (str "/" base-uri "/subscriptions")}))))
    
    (catch Exception e
      (log/error e "Failed to list subscriptions")
      (r/json-response (problem-details
                         "about:blank"
                         "Internal Server Error"
                         500
                         :detail (ex-message e)) 500))))


(defn get-subscription-handler
  "GET /app_lcm/v2/subscriptions/{id} - Get a specific subscription"
  [request]
  (try
    (let [subscription-id (get-in request [:params :id])
          user-id         (get-in request [:identity :user-id])
          sub             (subscription/get-subscription-by-id
                            @subscription-store
                            subscription-id)]
      
      (cond
        (nil? sub)
        (r/json-response (not-found-error subscription-id) 404)
        
        (and user-id (not= (:owner sub) user-id))
        (r/json-response (problem-details
                           "https://docs.nuvla.io/mec/errors/forbidden"
                           "Access Forbidden"
                           403
                           :detail "You do not have permission to access this subscription"
                           :instance subscription-id) 403)
        
        :else
        (r/json-response sub)))
    
    (catch Exception e
      (log/error e "Failed to get subscription")
      (r/json-response (problem-details
                         "about:blank"
                         "Internal Server Error"
                         500
                         :detail (ex-message e)) 500))))


(defn delete-subscription-handler
  "DELETE /app_lcm/v2/subscriptions/{id} - Delete a subscription"
  [request]
  (try
    (let [subscription-id (get-in request [:params :id])
          user-id         (get-in request [:identity :user-id])
          sub             (subscription/get-subscription-by-id
                            @subscription-store
                            subscription-id)]
      
      (cond
        (nil? sub)
        (r/json-response (not-found-error subscription-id) 404)
        
        (and user-id (not= (:owner sub) user-id))
        (r/json-response (problem-details
                           "https://docs.nuvla.io/mec/errors/forbidden"
                           "Access Forbidden"
                           403
                           :detail "You do not have permission to delete this subscription"
                           :instance subscription-id) 403)
        
        :else
        (do
          ;; Deactivate subscription (soft delete)
          (swap! subscription-store
                 (fn [subs]
                   (mapv (fn [s]
                           (if (= (:id s) subscription-id)
                             (subscription/deactivate-subscription s)
                             s))
                         subs)))
          
          (log/info "Deleted subscription" subscription-id)
          (r/json-response nil 204))))
    
    (catch Exception e
      (log/error e "Failed to delete subscription")
      (r/json-response (problem-details
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
