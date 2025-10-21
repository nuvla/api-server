(ns com.sixsq.nuvla.server.resources.mec.mock-mepm-server
  "Mock MEPM server for testing Mm5 interface.
   Implements ETSI MEC 003 Mm5 reference point for integration testing."
  (:require
    [clojure.tools.logging :as log]
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
         :app-instances   {}
         :request-count   0
         :error-mode      nil}))

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
                      :app-instances   {}
                      :request-count   0
                      :error-mode      nil}))

(defn set-error-mode!
  "Set error mode for testing error handling.
   Modes: :timeout, :server-error, :not-found, nil (normal)"
  [mode]
  (swap! mepm-state assoc :error-mode mode))

(defn get-state
  "Get current MEPM state."
  []
  @mepm-state)

;;
;; Mm5 Endpoint Handlers
;;

(defn- increment-request-count! []
  (swap! mepm-state update :request-count inc))

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
  "Handle GET /mm5/health - Check MEPM health status."
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
  "Handle GET /mm5/capabilities - Query MEPM capabilities."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   (:capabilities @mepm-state)}))

(defn handle-resources-query
  "Handle GET /mm5/resources - Query available resources."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   (:resources @mepm-state)}))

(defn handle-platform-info
  "Handle GET /mm5/platform-info - Get platform metadata."
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
  "Handle POST /mm5/configure - Configure platform settings."
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

(defn handle-create-app-instance
  "Handle POST /mm5/app-instances - Create application instance."
  [request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    (let [app-desc (:body request)
          app-id (str "app-" (java.util.UUID/randomUUID))
          instance {:id          app-id
                   :name        (:name app-desc)
                   :status      "INSTANTIATED"
                   :created     (str (java.time.Instant/now))
                   :descriptor  app-desc}]
      (swap! mepm-state assoc-in [:app-instances app-id] instance)
      {:status 201
       :body   instance})))

(defn handle-get-app-instance
  "Handle GET /mm5/app-instances/:id - Get application instance status."
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
  "Handle GET /mm5/app-instances - List all application instances."
  [_request]
  (increment-request-count!)
  (if-let [error-response (check-error-mode)]
    error-response
    {:status 200
     :body   {:instances (vals (:app-instances @mepm-state))}}))

(defn handle-delete-app-instance
  "Handle DELETE /mm5/app-instances/:id - Terminate application instance."
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
        (and (= method :get) (= path "/mm5/health"))
        (handle-health-check request)

        ;; Capabilities
        (and (= method :get) (= path "/mm5/capabilities"))
        (handle-capabilities-query request)

        ;; Resources
        (and (= method :get) (= path "/mm5/resources"))
        (handle-resources-query request)

        ;; Platform info
        (and (= method :get) (= path "/mm5/platform-info"))
        (handle-platform-info request)

        ;; Configure platform
        (and (= method :post) (= path "/mm5/configure"))
        (handle-configure-platform request)

        ;; App instances - list (must come before single instance match)
        (and (= method :get) (= path "/mm5/app-instances"))
        (handle-list-app-instances request)

        ;; App instances - create
        (and (= method :post) (= path "/mm5/app-instances"))
        (handle-create-app-instance request)

        ;; App instances - get single
        (and (= method :get) (re-matches #"/mm5/app-instances/(.+)" path))
        (let [app-id (second (re-matches #"/mm5/app-instances/(.+)" path))]
          (handle-get-app-instance request app-id))

        ;; App instances - delete
        (and (= method :delete) (re-matches #"/mm5/app-instances/(.+)" path))
        (let [app-id (second (re-matches #"/mm5/app-instances/(.+)" path))]
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

(defn create-handler
  "Create Ring handler for mock MEPM server."
  []
  (-> route-request
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
