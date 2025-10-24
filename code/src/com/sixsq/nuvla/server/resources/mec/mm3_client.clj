(ns com.sixsq.nuvla.server.resources.mec.mm3-client
  "Mm3 Interface Client - MEO to MEPM communication
   
   ETSI MEC 003 Reference Point Mm3:
   - Interface between MEC Orchestrator (MEO) and MEC Platform Manager (MEPM)
   - Used for management of application lifecycle, application rules and requirements
   - Handles platform management operations:
     * Platform health checks
     * Capability queries
     * Resource availability queries
     * Platform configuration
     * Application lifecycle management
   
   This is a REST-based client implementation."
  (:require
    [clj-http.client :as http]
    [clojure.tools.logging :as log]
    [jsonista.core :as json]))


;;
;; Configuration
;;

(def ^:private default-timeout-ms
  "Default HTTP timeout in milliseconds"
  30000)

(def ^:private default-connect-timeout-ms
  "Default HTTP connection timeout in milliseconds"
  10000)

(def ^:private default-retry-attempts
  "Default number of retry attempts for failed requests"
  3)

(def ^:private retry-delay-ms
  "Delay between retry attempts in milliseconds"
  1000)


;;
;; HTTP Client Utilities
;;

(defn- build-http-options
  "Build HTTP client options with standard settings"
  [endpoint {:keys [timeout connect-timeout insecure?]
             :or   {timeout         default-timeout-ms
                    connect-timeout default-connect-timeout-ms
                    insecure?       false}}]
  {:socket-timeout      timeout
   :connection-timeout  connect-timeout
   :insecure?           insecure?
   :throw-exceptions    false
   :as                  :json
   :content-type        :json
   :accept              :json
   :coerce              :always})


(defn- parse-response
  "Parse HTTP response and handle errors"
  [{:keys [status body] :as response}]
  (cond
    ;; Success
    (and (>= status 200) (< status 300))
    {:success? true
     :status   status
     :data     body}

    ;; Client error (4xx)
    (and (>= status 400) (< status 500))
    {:success? false
     :status   status
     :error    :client-error
     :message  (or (:message body)
                   (str "Client error: " status))}

    ;; Server error (5xx)
    (>= status 500)
    {:success? false
     :status   status
     :error    :server-error
     :message  (or (:message body)
                   (str "Server error: " status))}

    ;; Unknown error
    :else
    {:success? false
     :status   status
     :error    :unknown-error
     :message  "Unknown error occurred"}))


(defn- retry-request
  "Retry a request with exponential backoff"
  [request-fn max-attempts]
  (loop [attempt 1]
    (let [result (try
                   (request-fn)
                   (catch Exception e
                     {:success? false
                      :error    :exception
                      :message  (.getMessage e)
                      :exception e}))]
      (if (or (:success? result)
              (>= attempt max-attempts))
        result
        (do
          (log/debug "Retry attempt" attempt "/" max-attempts)
          (Thread/sleep (* retry-delay-ms attempt))
          (recur (inc attempt)))))))


;;
;; Mm3 API Operations
;;

(defn check-health
  "Perform health check on MEPM via Mm3 interface.
   
   ETSI MEC 003: Mm3 health check operation
   - Verifies MEPM is reachable and operational
   - Returns platform status and metrics
   
   Parameters:
   - endpoint: MEPM base URL (e.g., 'https://mepm.example.com:8443')
   - options: HTTP client options (optional)
     * :timeout - request timeout in ms (default: 30000)
     * :connect-timeout - connection timeout in ms (default: 10000)
     * :insecure? - allow insecure SSL (default: false)
     * :retry-attempts - number of retries (default: 3)
   
   Returns:
   - {:success? true :status 200 :data {...}} on success
   - {:success? false :error :xxx :message \"...\"} on failure"
  [endpoint & [{:keys [retry-attempts] 
                :or {retry-attempts default-retry-attempts}
                :as options}]]
  (log/info "Mm3: Checking health of MEPM at" endpoint)
  (let [url (str endpoint "/mm3/health")
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/get url http-opts)]
            (log/debug "Mm3 health check response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to check health")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn query-capabilities
  "Query MEPM capabilities via Mm3 interface.
   
   ETSI MEC 003: Mm3 capability query operation
   - Retrieves supported platforms, services, and API versions
   - Used for service discovery and compatibility checks
   
   Parameters:
   - endpoint: MEPM base URL
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :data {:platforms [...] :services [...] :api-version \"...\"}}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint & [{:keys [retry-attempts]
                :or {retry-attempts default-retry-attempts}
                :as options}]]
  (log/info "Mm3: Querying capabilities from MEPM at" endpoint)
  (let [url (str endpoint "/mm3/capabilities")
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/get url http-opts)]
            (log/debug "Mm3 capabilities response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to query capabilities")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn query-resources
  "Query available resources on MEPM via Mm3 interface.
   
   ETSI MEC 003: Mm3 resource query operation
   - Retrieves available compute, memory, storage, and GPU resources
   - Used for placement decisions and capacity planning
   
   Parameters:
   - endpoint: MEPM base URL
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :data {:cpu-cores N :memory-gb N :storage-gb N :gpu-count N}}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint & [{:keys [retry-attempts]
                :or {retry-attempts default-retry-attempts}
                :as options}]]
  (log/info "Mm3: Querying resources from MEPM at" endpoint)
  (let [url (str endpoint "/mm3/resources")
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/get url http-opts)]
            (log/debug "Mm3 resources response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to query resources")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn configure-platform
  "Configure MEPM platform settings via Mm3 interface.
   
   ETSI MEC 003: Mm3 platform configuration operation
   - Updates platform-level settings
   - Configures enabled services and features
   
   Parameters:
   - endpoint: MEPM base URL
   - config: Configuration map (e.g., {:service-registry true})
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :status 200}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint config & [{:keys [retry-attempts]
                       :or {retry-attempts default-retry-attempts}
                       :as options}]]
  (log/info "Mm3: Configuring MEPM at" endpoint "with config:" config)
  (let [url (str endpoint "/mm3/configure")
        http-opts (merge (build-http-options endpoint options)
                         {:body (json/write-value-as-string config)})]
    (retry-request
      (fn []
        (try
          (let [response (http/post url http-opts)]
            (log/debug "Mm3 configure response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to configure platform")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn get-platform-info
  "Get general platform information via Mm3 interface.
   
   ETSI MEC 003: Mm3 platform info operation
   - Retrieves platform metadata and status
   - Includes version, location, and operational state
   
   Parameters:
   - endpoint: MEPM base URL
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :data {:name \"...\" :version \"...\" :status \"...\"}}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint & [{:keys [retry-attempts]
                :or {retry-attempts default-retry-attempts}
                :as options}]]
  (log/info "Mm3: Getting platform info from MEPM at" endpoint)
  (let [url (str endpoint "/mm3/platform-info")
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/get url http-opts)]
            (log/debug "Mm3 platform info response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to get platform info")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


;;
;; Application Lifecycle Operations
;;

(defn create-app-instance
  "Create a new application instance via Mm3 interface.
   
   ETSI MEC 003: Mm3 application instantiation operation
   
   Parameters:
   - endpoint: MEPM base URL
   - app-descriptor: Application descriptor map
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :status 201 :data {:id \"...\" :status \"...\"}}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint app-descriptor & [{:keys [retry-attempts]
                               :or {retry-attempts default-retry-attempts}
                               :as options}]]
  (log/info "Mm3: Creating app instance on MEPM at" endpoint)
  (let [url (str endpoint "/mm3/app-instances")
        http-opts (build-http-options endpoint options)
        http-opts (assoc http-opts :body (json/write-value-as-string app-descriptor)
                                   :content-type :json)]
    (retry-request
      (fn []
        (try
          (let [response (http/post url http-opts)]
            (log/debug "Mm3 create app instance response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to create app instance")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn get-app-instance
  "Get application instance status via Mm3 interface.
   
   ETSI MEC 003: Mm3 application query operation
   
   Parameters:
   - endpoint: MEPM base URL
   - app-id: Application instance identifier
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :status 200 :data {:id \"...\" :status \"...\"}}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint app-id & [{:keys [retry-attempts]
                       :or {retry-attempts default-retry-attempts}
                       :as options}]]
  (log/info "Mm3: Getting app instance" app-id "from MEPM at" endpoint)
  (let [url (str endpoint "/mm3/app-instances/" app-id)
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/get url http-opts)]
            (log/debug "Mm3 get app instance response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to get app instance")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn list-app-instances
  "List all application instances via Mm3 interface.
   
   ETSI MEC 003: Mm3 application listing operation
   
   Parameters:
   - endpoint: MEPM base URL
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :status 200 :data {:instances [...]}}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint & [{:keys [retry-attempts]
                :or {retry-attempts default-retry-attempts}
                :as options}]]
  (log/info "Mm3: Listing app instances from MEPM at" endpoint)
  (let [url (str endpoint "/mm3/app-instances")
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/get url http-opts)]
            (log/debug "Mm3 list app instances response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to list app instances")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


(defn delete-app-instance
  "Delete (terminate) an application instance via Mm3 interface.
   
   ETSI MEC 003: Mm3 application termination operation
   
   Parameters:
   - endpoint: MEPM base URL
   - app-id: Application instance identifier
   - options: HTTP client options (optional)
   
   Returns:
   - {:success? true :status 204}
   - {:success? false :error :xxx :message \"...\"}"
  [endpoint app-id & [{:keys [retry-attempts]
                       :or {retry-attempts default-retry-attempts}
                       :as options}]]
  (log/info "Mm3: Deleting app instance" app-id "from MEPM at" endpoint)
  (let [url (str endpoint "/mm3/app-instances/" app-id)
        http-opts (build-http-options endpoint options)]
    (retry-request
      (fn []
        (try
          (let [response (http/delete url http-opts)]
            (log/debug "Mm3 delete app instance response:" response)
            (parse-response response))
          (catch Exception e
            (log/error e "Mm3: Failed to delete app instance")
            {:success? false
             :error    :connection-error
             :message  (.getMessage e)
             :exception e})))
      retry-attempts)))


;;
;; Convenience functions
;;

(defn healthy?
  "Check if MEPM is healthy (returns true/false)"
  [endpoint & [options]]
  (let [result (check-health endpoint options)]
    (and (:success? result)
         (= 200 (:status result)))))


(defn get-capabilities
  "Get capabilities or nil if unavailable"
  [endpoint & [options]]
  (let [result (query-capabilities endpoint options)]
    (when (:success? result)
      (:data result))))


(defn get-resources
  "Get resources or nil if unavailable"
  [endpoint & [options]]
  (let [result (query-resources endpoint options)]
    (when (:success? result)
      (:data result))))
