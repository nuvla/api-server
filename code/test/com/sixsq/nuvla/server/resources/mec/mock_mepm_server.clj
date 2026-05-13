(ns com.sixsq.nuvla.server.resources.mec.mock-mepm-server
  "Mock MEPM server for testing the selected Mm3 interface path.
   Implements ETSI MEC 003 southbound endpoints for integration testing."
  (:require
    [clj-http.client :as http]
    [clojure.tools.logging :as log]
    [jsonista.core :as json]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as response]))

;;
;; Mock MEPM State
;;

(defonce ^:private mepm-state
  (atom {:status          :online
         :capabilities    {:platforms        ["kubernetes" "docker"]
                          :services         ["app-lifecycle" "traffic-rules"]
                          :api-version      "3.1.1"
                          :mec-version      "3.1.1"
                          :supported-vnfds  ["container" "vm"]}
         :resources       {:cpu-cores   64
                          :memory-gb   256
                          :storage-gb  2000
                          :gpu-count   4
                          :available   {:cpu-cores  32
                                       :memory-gb  128
                                       :storage-gb 1000
                                       :gpu-count  2}}
         :subscriptions   {}
         :app-instances   {}
         :operations      {}
         :request-count   0
         :error-mode      nil}))

(def ^:private max-request-log-size 200)

(defonce ^:private request-log
  (atom []))

(def ^:private mm3-lifecycle-base-path
  "/mm3/app_lcm/v1")

(def ^:private lifecycle-subscriptions-path
  (str mm3-lifecycle-base-path "/subscriptions"))

(def ^:private lifecycle-app-instances-path
  (str mm3-lifecycle-base-path "/app_instances"))

(def ^:private lifecycle-op-occs-re
  (re-pattern (str mm3-lifecycle-base-path "/app_lcm_op_occs/(.+)")))

(def ^:private lifecycle-app-instance-re
  (re-pattern (str lifecycle-app-instances-path "/(.+)")))

(def ^:private lifecycle-app-instance-operate-re
  (re-pattern (str lifecycle-app-instances-path "/(.+)/operate")))

(defn reset-state!
  "Reset MEPM state to defaults."
  []
  (reset! mepm-state {:status          :online
                      :capabilities    {:platforms        ["kubernetes" "docker"]
                                       :services         ["app-lifecycle" "traffic-rules"]
                                       :api-version      "3.1.1"
                                       :mec-version      "3.1.1"
                                       :supported-vnfds  ["container" "vm"]}
                      :resources       {:cpu-cores   64
                                       :memory-gb   256
                                       :storage-gb  2000
                                       :gpu-count   4
                                       :available   {:cpu-cores  32
                                                    :memory-gb  128
                                                    :storage-gb 1000
                                                    :gpu-count  2}}
                      :subscriptions   {}
                      :app-instances   {}
                      :operations      {}
                      :request-count   0
                      :error-mode      nil})
  (reset! request-log []))

(defn set-error-mode!
  "Set error mode for testing error handling.
   Modes: :timeout, :server-error, :not-found, nil (normal)"
  [mode]
  (swap! mepm-state assoc :error-mode mode))

(defn get-state
  "Get current MEPM state."
  []
  @mepm-state)

(defn clear-request-log!
  "Clear the bounded request log used for demo inspection."
  []
  (reset! request-log []))

(defn get-request-log
  "Get the current bounded request log."
  []
  @request-log)

;;
;; Mm3 Endpoint Handlers
;;

(defn- increment-request-count! []
  (swap! mepm-state update :request-count inc))

(defn- request-operation
  [{:keys [request-method uri]}]
  (cond
    (and (= request-method :get) (= uri "/mm3/health")) "health"
    (and (= request-method :get) (= uri "/mm3/capabilities")) "capabilities"
    (and (= request-method :get) (= uri "/mm3/resources")) "resources"
    (and (= request-method :get) (= uri "/mm3/platform-info")) "platform-info"
    (and (= request-method :post) (= uri "/mm3/configure")) "configure"
    (and (= request-method :post) (= uri lifecycle-subscriptions-path)) "create-subscription"
    (and (= request-method :post) (= uri "/mm3/test/emit-notification")) "emit-notification"
    (and (= request-method :get) (= uri lifecycle-app-instances-path)) "list-app-instances"
    (and (= request-method :post) (= uri lifecycle-app-instances-path)) "create-app-instance"
    (and (= request-method :post) (re-matches lifecycle-app-instance-operate-re uri)) "operate-app-instance"
    (and (= request-method :get) (re-matches lifecycle-app-instance-re uri)) "get-app-instance"
    (and (= request-method :delete) (re-matches lifecycle-app-instance-re uri)) "delete-app-instance"
    :else "unknown"))

(defn- append-request-log!
  [request response]
  (let [entry {:timestamp (str (java.time.Instant/now))
               :method    (some-> (:request-method request) name)
               :uri       (:uri request)
               :operation (request-operation request)
               :caller    (get-in request [:headers "x-nuvla-mm3-caller"])
               :status    (:status response)
               :body      (:body request)}]
    (swap! request-log
           (fn [entries]
             (->> (conj entries entry)
                  (take-last max-request-log-size)
                  vec)))))

(defn- check-error-mode
  "Check if we should simulate an error based on error-mode."
  []
  (when-let [mode (:error-mode @mepm-state)]
    (case mode
      :timeout {:status 504 :body {:error "Gateway Timeout" :message "MEPM not responding"}}
      :server-error {:status 500 :body {:error "Internal Server Error" :message "MEPM internal error"}}
      :not-found {:status 404 :body {:error "Not Found" :message "MEPM not found"}}
      :degraded {:status 503 :body {:error "Service Unavailable" :message "MEPM degraded"}}
      nil)))

(defn handle-health-check
  "Handle GET /mm3/health - Check MEPM health status."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [state @mepm-state]
      {:status 200
       :body   {:status         (name (:status state))
                :timestamp      (str (java.time.Instant/now))
                :version        "1.0.0"
                :uptime-seconds 3600
                :checks         {:database    "healthy"
                                :mep         "healthy"
                                :vim         "healthy"}}})))

(defn handle-capabilities-query
  "Handle GET /mm3/capabilities - Query MEPM capabilities."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   (:capabilities @mepm-state)}))

(defn handle-resources-query
  "Handle GET /mm3/resources - Query available resources."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   (:resources @mepm-state)}))

(defn handle-platform-info
  "Handle GET /mm3/platform-info - Get platform metadata."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   {:platform-id   "mock-mepm-1"
              :platform-name "Mock MEPM Server"
              :location      {:latitude  48.8566
                             :longitude 2.3522
                             :city      "Paris"
                             :country   "France"}
              :operator      "Mock Operator"
              :created       "2025-01-01T00:00:00Z"}}))

(defn handle-configure-platform
  "Handle POST /mm3/configure - Configure platform settings."
  [request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [config (:body request)]
      (log/info "Configuring platform with:" config)
      {:status 200
       :body   {:success true
                :message "Platform configured successfully"
                :config  config}})))

(defn handle-create-subscription
  "Handle POST /mm3/app_lcm/v1/subscriptions - Create lifecycle notification subscription."
  [request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [subscription (:body request)
          subscription-id (str "sub-" (java.util.UUID/randomUUID))
          persisted       (assoc subscription
                            :id subscription-id
                            :created (str (java.time.Instant/now)))]
      (swap! mepm-state assoc-in [:subscriptions subscription-id] persisted)
      {:status 201
       :body   persisted})))

(defn- reconcile-app-instance-notification!
  [{:keys [appInstanceId instantiationState operationalState] :as _notification}]
  (when appInstanceId
    (swap! mepm-state
           (fn [state]
             (let [current-instance (get-in state [:app-instances appInstanceId] {:id appInstanceId})
                   updated-instance (cond-> current-instance
                                      instantiationState (assoc :instantiationState instantiationState)
                                      operationalState (assoc :operationalState operationalState)
                                      (= "NOT_INSTANTIATED" instantiationState) (dissoc :operationalState))]
               (assoc-in state [:app-instances appInstanceId] updated-instance))))))

(defn handle-emit-notification
  "Handle POST /mm3/test/emit-notification - emit a stored notification to a callback URI."
  [request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [{:keys [subscriptionId callbackUri notification]} (:body request)
          callback-uri (or callbackUri
                           (get-in @mepm-state [:subscriptions subscriptionId :callbackUri]))]
      (cond
        (nil? callback-uri)
        {:status 404
         :body   {:error "Not Found"
                  :message "Notification callback URI not found"}}

        (nil? notification)
        {:status 400
         :body   {:error "Bad Request"
                  :message "Notification payload is required"}}

        :else
        (let [_ (when (= "AppInstNotification" (:notificationType notification))
                  (reconcile-app-instance-notification! notification))
              response (http/post callback-uri
                                  {:body             (json/write-value-as-string notification)
                                   :content-type     :json
                                   :accept           :json
                                   :throw-exceptions false
                                   :as               :json
                                   :coerce           :always})]
          {:status 202
           :body   {:callbackUri      callback-uri
                    :callbackStatus   (:status response)
                    :subscriptionId   subscriptionId
                    :notificationType (:notificationType notification)}})))))

(defn handle-create-app-instance
  "Handle POST /mm3/app_lcm/v1/app_instances - Create application instance."
  [request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [app-desc (:body request)
          app-id (str "app-" (java.util.UUID/randomUUID))
          op-id  (str "op-" (java.util.UUID/randomUUID))
          instance {:id          app-id
                   :name        (:name app-desc)
                   :instantiationState "INSTANTIATED"
                   :operationalState   "STARTED"
                   :created     (str (java.time.Instant/now))
                   :descriptor  app-desc}]
      (swap! mepm-state assoc-in [:app-instances app-id] instance)
      (swap! mepm-state assoc-in [:operations op-id]
             {:id            op-id
              :operationType "INSTANTIATE"
              :appInstanceId app-id
              :status        "COMPLETED"})
      {:status 201
       :body   (assoc instance :operationId op-id)})))

(defn handle-get-app-instance
  "Handle GET /mm3/app_lcm/v1/app_instances/:id - Get application instance status."
  [request app-id]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [instance (get-in @mepm-state [:app-instances app-id])]
      (if instance
        {:status 200
         :body   instance}
        {:status 404
         :body   {:error "Not Found" :message (str "Application instance " app-id " not found")}}))))

(defn handle-list-app-instances
  "Handle GET /mm3/app_lcm/v1/app_instances - List all application instances."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   {:instances (vals (:app-instances @mepm-state))}}))

(defn handle-delete-app-instance
  "Handle DELETE /mm3/app_lcm/v1/app_instances/:id - Terminate application instance."
  [request app-id]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (if (get-in @mepm-state [:app-instances app-id])
      (do
        (swap! mepm-state update :app-instances dissoc app-id)
        {:status 204})
      {:status 404
       :body   {:error "Not Found" :message (str "Application instance " app-id " not found")}})))

(defn handle-get-operation
  "Handle GET /mm3/app_lcm/v1/app_lcm_op_occs/:id - Get lifecycle operation status."
  [request operation-id]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [operation (get-in @mepm-state [:operations operation-id])]
      (if operation
        {:status 200
         :body   operation}
        {:status 404
         :body   {:error "Not Found"
                  :message (str "Operation " operation-id " not found")}}))))

(defn handle-operate-app-instance
  "Handle POST /mm3/app_lcm/v1/app_instances/:id/operate - Change application instance state."
  [request app-id]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [change-state-to (get-in request [:body :changeStateTo])
          instance (get-in @mepm-state [:app-instances app-id])]
      (cond
        (nil? instance)
        {:status 404
         :body   {:error "Not Found" :message (str "Application instance " app-id " not found")}}

        (not (#{"STARTED" "STOPPED"} change-state-to))
        {:status 400
         :body   {:error "Bad Request" :message (str "Unsupported changeStateTo: " change-state-to)}}

        :else
        (let [op-id            (str "op-" (java.util.UUID/randomUUID))
              updated-instance (assoc instance
                                      :instantiationState "INSTANTIATED"
                                      :operationalState change-state-to)]
          (swap! mepm-state assoc-in [:app-instances app-id] updated-instance)
          (swap! mepm-state assoc-in [:operations op-id]
                 {:id            op-id
                  :operationType "OPERATE"
                  :appInstanceId app-id
                  :status        "COMPLETED"
                  :targetState   change-state-to})
          {:status 200
           :body   (assoc updated-instance :operationId op-id)})))))

;;
;; Router
;;

(defn- route-request
  "Route incoming requests to appropriate handlers."
  [request]
  (let [method (:request-method request)
        path   (:uri request)]
    (log/debug "Mock MEPM received:" method path)
    (try
      (cond
        ;; Health check
        (and (= method :get) (= path "/mm3/health"))
        (handle-health-check request)

        ;; Capabilities
        (and (= method :get) (= path "/mm3/capabilities"))
        (handle-capabilities-query request)

        ;; Resources
        (and (= method :get) (= path "/mm3/resources"))
        (handle-resources-query request)

        ;; Platform info
        (and (= method :get) (= path "/mm3/platform-info"))
        (handle-platform-info request)

        ;; Configure platform
        (and (= method :post) (= path "/mm3/configure"))
        (handle-configure-platform request)

        ;; Subscriptions - create
        (and (= method :post) (= path lifecycle-subscriptions-path))
        (handle-create-subscription request)

        ;; Test helper - emit notification to callback
        (and (= method :post) (= path "/mm3/test/emit-notification"))
        (handle-emit-notification request)

        ;; Operations - get single
        (and (= method :get) (re-matches lifecycle-op-occs-re path))
        (let [operation-id (second (re-matches lifecycle-op-occs-re path))]
          (handle-get-operation request operation-id))

        ;; App instances - list (must come before single instance match)
        (and (= method :get) (= path lifecycle-app-instances-path))
        (handle-list-app-instances request)

        ;; App instances - create
        (and (= method :post) (= path lifecycle-app-instances-path))
        (handle-create-app-instance request)

        ;; App instances - get single
        (and (= method :get) (re-matches lifecycle-app-instance-re path))
        (let [app-id (second (re-matches lifecycle-app-instance-re path))]
          (handle-get-app-instance request app-id))

        ;; App instances - operate
        (and (= method :post) (re-matches lifecycle-app-instance-operate-re path))
        (let [app-id (second (re-matches lifecycle-app-instance-operate-re path))]
          (handle-operate-app-instance request app-id))

        ;; App instances - delete
        (and (= method :delete) (re-matches lifecycle-app-instance-re path))
        (let [app-id (second (re-matches lifecycle-app-instance-re path))]
          (handle-delete-app-instance request app-id))

        ;; Not found
        :else
        {:status 404
         :body   {:error "Not Found" :message (str "Unknown endpoint: " method " " path)}})
      (catch Exception e
        (log/error e "Error handling request")
        {:status 500
         :body   {:error "Internal Server Error" :message (.getMessage e)}}))))

(defn- wrap-logging [handler]
  (fn [request]
    (log/debug "Mock MEPM Request:" (:request-method request) (:uri request))
    (let [response (handler request)]
      (log/debug "Mock MEPM Response:" (:status response))
      response)))

(defn- wrap-request-log [handler]
  (fn [request]
    (let [response (handler request)]
      (append-request-log! request response)
      response)))

(defn create-handler
  "Create Ring handler for mock MEPM server."
  []
  (-> route-request
      wrap-request-log
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-params
      wrap-logging))

;;
;; Server Lifecycle
;;

(defonce ^:private server (atom nil))

(declare stop-server!)

(defn start-server!
  "Start mock MEPM server on specified port."
  ([port]
   (start-server! port {}))
  ([port options]
   (when @server
     (stop-server!))
   (reset-state!)
   (log/info "Starting mock MEPM server on port" port)
   (let [server-instance (jetty/run-jetty
                          (create-handler)
                          (merge {:port  port
                                  :join? false}
                                 options))]
     (reset! server server-instance)
     (log/info "Mock MEPM server started on port" port)
     server-instance)))

(defn stop-server!
  "Stop mock MEPM server."
  []
  (when-let [s @server]
    (log/info "Stopping mock MEPM server")
    (.stop s)
    (reset! server nil)
    (log/info "Mock MEPM server stopped")))

(defn restart-server!
  "Restart mock MEPM server on specified port."
  ([port]
   (restart-server! port {}))
  ([port options]
   (stop-server!)
   (Thread/sleep 100) ; Brief pause to ensure port is released
   (start-server! port options)))

(defn server-running?
  "Check if mock MEPM server is running."
  []
  (some? @server))

;;
;; Test Utilities
;;

(defn with-mock-mepm
  "Test fixture to run tests with mock MEPM server.
   Usage: (with-mock-mepm 8080 (fn [] (run-tests)))"
  [port test-fn]
  (try
    (start-server! port)
    (test-fn)
    (finally
      (stop-server!))))

(defmacro with-mock-mepm-server
  "Macro to run tests with mock MEPM server.
   Usage: (with-mock-mepm-server 8080 (test-something))"
  [port & body]
  `(with-mock-mepm ~port (fn [] ~@body)))
