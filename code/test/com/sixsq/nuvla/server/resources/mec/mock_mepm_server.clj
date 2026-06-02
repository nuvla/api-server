(ns com.sixsq.nuvla.server.resources.mec.mock-mepm-server
  "Mock MEPM server for testing the selected Mm3 interface path.
   Implements ETSI MEC 003 southbound endpoints for integration testing."
  (:require
    [clj-http.client :as http]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.nuvla-backed-mepm :as nuvla-backed-mepm]
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
         :backend-mode    "MOCK"
         :mepm-id         "mepm/mock-server"
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

(def ^:private backed-poll-interval-ms
  2000)

(def ^:private backed-poll-max-attempts
  150)

(def ^:private mock-notification-delay-ms
  ;; Plain MOCK operations complete immediately, but the northbound job may need
  ;; a short window to persist `mec-southbound-operation-id` before the Mm3
  ;; callback arrives and attempts correlation.
  1000)

(def ^:private persisted-runtime-state-keys
  [:mepm-id :backend-mode :subscriptions])

(defn- runtime-state-file
  [port]
  (str (System/getProperty "user.dir")
       "/.nuvla-mock-mepm-"
       port
       ".json"))

(defonce ^:private operation-watchers
  (atom {}))

(defonce ^:private app-instance-reconciler
  (atom nil))

(declare restore-local-runtime-state!)

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

(defn- cancel-operation-watchers!
  []
  (doseq [[_ watcher] @operation-watchers]
    (future-cancel watcher))
  (reset! operation-watchers {}))

(defn- cancel-app-instance-reconciler!
  []
  (when-let [reconciler @app-instance-reconciler]
    (future-cancel reconciler)
    (reset! app-instance-reconciler nil)))

(defn reset-state!
  "Reset MEPM state to defaults."
  []
  (when-let [server-port (:server-port @mepm-state)]
    (let [state-file (runtime-state-file server-port)]
      (when (.exists (io/file state-file))
        (io/delete-file state-file true))))
  (cancel-operation-watchers!)
  (cancel-app-instance-reconciler!)
  (reset! mepm-state {:status          :online
                      :backend-mode    "MOCK"
                      :mepm-id         "mepm/mock-server"
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

(defn set-backend-mode!
  "Set mock MEPM backend mode."
  [mode]
  (swap! mepm-state assoc :backend-mode mode)
  (when-let [server-port (:server-port @mepm-state)]
    (spit (runtime-state-file server-port)
          (json/write-value-as-string
            (select-keys @mepm-state persisted-runtime-state-keys)))))

(defn set-nuvla-auth!
  "Configure how the NUVLA_BACKED mock MEPM authenticates back into Nuvla."
  [endpoint api-key api-secret]
  (nuvla-backed-mepm/set-nuvla-auth! {:endpoint endpoint
                                      :api-key api-key
                                      :api-secret api-secret}))

(defn clear-nuvla-debug-log!
  "Clear the buffered reverse Nuvla API debug log for NUVLA_BACKED mode."
  []
  (nuvla-backed-mepm/clear-debug-log!))

(defn get-nuvla-debug-log
  "Return the buffered reverse Nuvla API debug log for NUVLA_BACKED mode."
  []
  (nuvla-backed-mepm/get-debug-log))

(defn set-mepm-id!
  "Set the MEPM id reported by the mock server when running NUVLA_BACKED flows."
  [mepm-id]
  (swap! mepm-state assoc :mepm-id mepm-id)
  (when-let [server-port (:server-port @mepm-state)]
    (spit (runtime-state-file server-port)
          (json/write-value-as-string
            (select-keys @mepm-state persisted-runtime-state-keys)))))

(defn get-state
  "Get current MEPM state."
  []
  @mepm-state)

(defn clear-request-log!
  "Clear the bounded request log used for demo inspection."
  []
  (reset! request-log []))

(declare reconcile-app-instance-notification!
         recover-lifecycle-subscription-from-nuvla!
         recover-app-instances-from-nuvla!)

(defn- now-str
  []
  (str (java.time.Instant/now)))

(defn- emit-notification-to-callback!
  [callback-uri notification]
  (when (= "AppInstNotification" (:notificationType notification))
    (reconcile-app-instance-notification! notification))
  (http/post callback-uri
             {:body             (json/write-value-as-string notification)
              :content-type     :json
              :accept           :json
              :throw-exceptions false
              :as               :json
              :coerce           :always}))

(defn- ensure-local-runtime-state-loaded!
  []
  (when (empty? (:subscriptions @mepm-state))
    (when-let [server-port (:server-port @mepm-state)]
      (restore-local-runtime-state! server-port))))

(defn- emit-notification-to-subscribers!
  [notification-builder]
  (ensure-local-runtime-state-loaded!)
  (doseq [[subscription-id {:keys [callbackUri active]}] (:subscriptions @mepm-state)
          :when (and callbackUri (not= false active))]
    (let [notification (notification-builder subscription-id)
          response     (emit-notification-to-callback! callbackUri notification)]
      (log/info "Mock MEPM delivered notification"
                {:subscription-id subscription-id
                 :notification-type (:notificationType notification)
                 :callback-uri callbackUri
                 :status (:status response)}))))

(defn- schedule-notification-to-subscribers!
  [notification-builder]
  (future
    (Thread/sleep mock-notification-delay-ms)
    (emit-notification-to-subscribers! notification-builder)))

(defn- persist-local-runtime-state!
  []
  (when-let [server-port (:server-port @mepm-state)]
    (spit (runtime-state-file server-port)
          (json/write-value-as-string
            (select-keys @mepm-state persisted-runtime-state-keys)))))

(defn- restore-local-runtime-state!
  [port]
  (let [state-file (runtime-state-file port)]
    (when (.exists (io/file state-file))
      (try
        (let [persisted (json/read-value (slurp state-file) json/keyword-keys-object-mapper)
              restored  (cond-> (select-keys persisted persisted-runtime-state-keys)
                          (:subscriptions persisted)
                          (assoc :subscriptions
                                 (into {}
                                       (map (fn [[subscription-id subscription]]
                                              [(str subscription-id) subscription]))
                                       (:subscriptions persisted))))]
          (swap! mepm-state merge restored)
          restored)
        (catch Exception e
          (log/warn e "Failed to restore mock MEPM runtime state from" state-file)
          nil)))))

(defn- operation-error-detail
  [backing]
  (or (:status-message backing)
      (get-in backing [:error :detail])
      (get-in backing [:error :message])
      (some-> (:state backing) (->> (str "Backing deployment entered ")))))

(defn- terminal-backing-outcome
  [{:keys [operationType targetState]} backing]
  (let [state        (:state backing)
        error-detail (operation-error-detail backing)]
    (case operationType
      "INSTANTIATE"
      (case state
        "STARTED" {:operationState "COMPLETED"
                   :instantiationState "INSTANTIATED"
                   :operationalState "STARTED"}
        "ERROR"   {:operationState "FAILED"
                   :instantiationState "NOT_INSTANTIATED"
                   :error {:detail (or error-detail "Backing deployment failed during instantiate")}}
        "STOPPED" {:operationState "FAILED"
                   :instantiationState "NOT_INSTANTIATED"
                   :error {:detail (or error-detail "Backing deployment stopped before instantiate completed")}}
        "CREATED" nil
        nil)

      "OPERATE"
      (case targetState
        "STARTED"
        (case state
          "STARTED" {:operationState "COMPLETED"
                     :instantiationState "INSTANTIATED"
                     :operationalState "STARTED"}
          "ERROR"   {:operationState "FAILED"
                     :instantiationState "INSTANTIATED"
                     :operationalState "STOPPED"
                     :error {:detail (or error-detail "Backing deployment failed while starting")}}
          "STOPPED" {:operationState "FAILED"
                     :instantiationState "INSTANTIATED"
                     :operationalState "STOPPED"
                     :error {:detail (or error-detail "Backing deployment did not reach STARTED")}}
          nil)

        "STOPPED"
        (case state
          "STOPPED" {:operationState "COMPLETED"
                     :instantiationState "INSTANTIATED"
                     :operationalState "STOPPED"}
          "CREATED" {:operationState "COMPLETED"
                     :instantiationState "INSTANTIATED"
                     :operationalState "STOPPED"}
          "ERROR"   {:operationState "FAILED"
                     :instantiationState "INSTANTIATED"
                     :operationalState "STARTED"
                     :error {:detail (or error-detail "Backing deployment failed while stopping")}}
          nil)
        nil)

      "TERMINATE"
      (case state
        "STOPPED" {:operationState "COMPLETED"
                   :instantiationState "NOT_INSTANTIATED"}
        "CREATED" {:operationState "COMPLETED"
                   :instantiationState "NOT_INSTANTIATED"}
        "ERROR"   {:operationState "FAILED"
                   :instantiationState "INSTANTIATED"
                   :operationalState "STARTED"
                   :error {:detail (or error-detail "Backing deployment failed while terminating")}}
        nil)

      nil)))

(defn- apply-backed-operation-outcome!
  [{:keys [id appInstanceId operationType] :as operation} outcome]
  (swap! mepm-state
         (fn [state]
           (let [current-instance (get-in state [:app-instances appInstanceId] {:id appInstanceId})
                 updated-instance (cond-> current-instance
                                    (:instantiationState outcome) (assoc :instantiationState (:instantiationState outcome))
                                    (contains? outcome :operationalState)
                                    ((fn [instance]
                                       (if-let [operational-state (:operationalState outcome)]
                                         (assoc instance :operationalState operational-state)
                                         (dissoc instance :operationalState)))))
                 updated-operation (cond-> (get-in state [:operations id] operation)
                                     true (assoc :status (:operationState outcome)
                                                 :operationState (:operationState outcome)
                                                 :stateEnteredTime (now-str))
                                     (:error outcome) (assoc :error (:error outcome)))]
             (cond-> (assoc-in state [:operations id] updated-operation)
               (not (and (= "TERMINATE" operationType)
                         (= "COMPLETED" (:operationState outcome))))
               (assoc-in [:app-instances appInstanceId] updated-instance)

               (and (= "TERMINATE" operationType)
                    (= "COMPLETED" (:operationState outcome)))
               (update :app-instances dissoc appInstanceId))))))

(defn- emit-operation-outcome-notification!
  [{:keys [id appInstanceId operationType]} outcome]
  (emit-notification-to-subscribers!
    (fn [subscription-id]
      (cond-> {:notificationType "AppLcmOpOccNotification"
               :subscriptionId   subscription-id
               :operationId      id
               :appInstanceId    appInstanceId
               :operationType    operationType
               :operationState   (:operationState outcome)}
        (:instantiationState outcome) (assoc :instantiationState (:instantiationState outcome))
        (contains? outcome :operationalState) (assoc :operationalState (:operationalState outcome))
        (:error outcome) (assoc :error (:error outcome))))))

(defn- mock-terminal-outcome
  [{:keys [operationType targetState]}]
  (case operationType
    "INSTANTIATE" {:operationState "COMPLETED"
                   :instantiationState "INSTANTIATED"
                   :operationalState "STARTED"}
    "OPERATE"     {:operationState "COMPLETED"
                   :instantiationState "INSTANTIATED"
                   :operationalState targetState}
    "TERMINATE"   {:operationState "COMPLETED"
                   :instantiationState "NOT_INSTANTIATED"}
    nil))

(defn- poll-backed-operation-step!
  [operation-id]
  (when-let [operation (get-in @mepm-state [:operations operation-id])]
    (try
      (let [backing (nuvla-backed-mepm/retrieve-backing-deployment! (:appInstanceId operation))]
        (when-let [outcome (terminal-backing-outcome operation backing)]
          (apply-backed-operation-outcome! operation outcome)
          (emit-operation-outcome-notification! operation outcome)
          :completed))
      (catch clojure.lang.ExceptionInfo e
        (if (and (= "TERMINATE" (:operationType operation))
                 (= 404 (:status (ex-data e))))
          (let [outcome {:operationState "COMPLETED"
                         :instantiationState "NOT_INSTANTIATED"}]
            (apply-backed-operation-outcome! operation outcome)
            (emit-operation-outcome-notification! operation outcome)
            :completed)
          (throw e))))))

(defn- watch-backed-operation!
  [operation-id]
  (let [watcher (future
                  (loop [attempt 1]
                    (let [result (try
                                   (poll-backed-operation-step! operation-id)
                                   (catch Exception e
                                     (log/warn e "Mock MEPM backing operation poll failed" operation-id)
                                     nil))]
                      (cond
                        (= :completed result)
                        (swap! operation-watchers dissoc operation-id)

                        (>= attempt backed-poll-max-attempts)
                        (do
                          (log/warn "Mock MEPM backing operation poll timed out" operation-id)
                          (swap! operation-watchers dissoc operation-id))

                        :else
                        (do
                          (Thread/sleep backed-poll-interval-ms)
                          (recur (inc attempt)))))))]
    (swap! operation-watchers assoc operation-id watcher)
    watcher))

(defn- processing-operation?
  [{:keys [status operationState]}]
  (or (= "PROCESSING" status)
      (= "PROCESSING" operationState)))

(defn- backing->app-instance-notification
  [app-instance-id backing]
  (let [state (some-> (:state backing) str str/upper-case)]
    (case state
      "STARTED" {:notificationType   "AppInstNotification"
                 :appInstanceId      app-instance-id
                 :instantiationState "INSTANTIATED"
                 :operationalState   "STARTED"}
      "STOPPED" {:notificationType   "AppInstNotification"
                 :appInstanceId      app-instance-id
                 :instantiationState "INSTANTIATED"
                 :operationalState   "STOPPED"}
      "CREATED" {:notificationType   "AppInstNotification"
                 :appInstanceId      app-instance-id
                 :instantiationState "NOT_INSTANTIATED"}
      nil)))

(defn- notification-matches-instance?
  [instance {:keys [instantiationState operationalState]}]
  (and (= instantiationState (:instantiationState instance))
       (= operationalState (:operationalState instance))))

(defn- emit-app-instance-notification!
  [notification]
  (emit-notification-to-subscribers!
    (fn [subscription-id]
      (assoc notification :subscriptionId subscription-id))))

(defn- reconcile-backed-app-instance-step!
  [app-instance-id instance]
  (let [notification (try
                       (some->> (nuvla-backed-mepm/retrieve-backing-deployment! app-instance-id)
                                (backing->app-instance-notification app-instance-id))
                       (catch clojure.lang.ExceptionInfo e
                         (when (= 404 (:status (ex-data e)))
                           {:notificationType   "AppInstNotification"
                            :appInstanceId      app-instance-id
                            :instantiationState "NOT_INSTANTIATED"})))]
    (when (and notification
               (not (notification-matches-instance? instance notification)))
      (emit-app-instance-notification! notification)
      :updated)))

(defn- reconcile-backed-app-instances-step!
  []
  (when (empty? (:subscriptions @mepm-state))
    (try
      (recover-lifecycle-subscription-from-nuvla!)
      (catch Exception e
        (log/warn e "Failed to recover mock MEPM subscription from Nuvla"))))
  (when (empty? (:app-instances @mepm-state))
    (try
      (recover-app-instances-from-nuvla!)
      (catch Exception e
        (log/warn e "Failed to recover mock MEPM app instances from Nuvla"))))
  (when (= "NUVLA_BACKED" (:backend-mode @mepm-state))
    (doseq [[app-instance-id instance] (:app-instances @mepm-state)]
      (try
        (reconcile-backed-app-instance-step! app-instance-id instance)
        (catch Exception e
          (log/warn e "Mock MEPM backing app-instance reconcile failed" app-instance-id))))))

(defn- start-app-instance-reconciler!
  []
  (when-not @app-instance-reconciler
    (reset! app-instance-reconciler
            (future
              (loop []
                (try
                  (reconcile-backed-app-instances-step!)
                  (catch Exception e
                    (log/warn e "Mock MEPM app-instance reconciler loop failed")))
                (Thread/sleep backed-poll-interval-ms)
                (recur))))))

(defn- recovered-app-instance
  [deployment]
  (when-let [backing-id (:mec-backing-deployment-id deployment)]
    (let [info (app-instance/deployment->app-instance-info deployment)]
      (cond-> {:id          backing-id
               :name        (or (:appName info)
                                (:name deployment)
                                backing-id)
               :created     (:created deployment)
               :backendMode "NUVLA_BACKED"
               :descriptor  {:appInstanceId (:id deployment)
                             :appDId        (get-in deployment [:module :href])
                             :mecHostId     (or (:mec-host-id deployment)
                                                (:nuvlabox deployment)
                                                (:parent deployment))
                             :name          (:name deployment)}}
        (:instantiationState info) (assoc :instantiationState (:instantiationState info))
        (:operationalState info) (assoc :operationalState (:operationalState info))))))

(defn recover-app-instances-from-nuvla!
  "Rehydrate tracked southbound app instances from persisted MEC-facing
   deployments stored in Nuvla."
  []
  (when-let [mepm-id (:mepm-id @mepm-state)]
    (let [deployments (nuvla-backed-mepm/query-nuvla-resources!
                        "deployment"
                        (str "mec-mepm-id='" mepm-id "'")
                        {:last 200})
          instances   (->> deployments
                           (filter #(and (= (:id %) (:mec-app-instance-id %))
                                         (:mec-backing-deployment-id %)))
                           (map recovered-app-instance)
                           (remove nil?)
                           (map (juxt :id identity))
                           (into {}))]
      (when (seq instances)
        (swap! mepm-state update :app-instances merge instances))
      instances)))

(defn recover-lifecycle-subscription-from-nuvla!
  "Rehydrate the local in-memory subscription registry from the persisted MEPM
   resource stored in Nuvla."
  []
  (when-let [mepm-id (:mepm-id @mepm-state)]
    (when-let [mepm (nuvla-backed-mepm/retrieve-nuvla-resource! mepm-id)]
      (let [subscription-id (or (:mm3-subscription-id mepm)
                                (get-in mepm [:subscription :id]))
            callback-uri    (or (:mm3-subscription-callback-uri mepm)
                                (get-in mepm [:subscription :callbackUri]))]
        (when (and subscription-id callback-uri)
          (swap! mepm-state assoc-in [:subscriptions subscription-id]
                 {:id                   subscription-id
                  :subscriptionId       subscription-id
                  :callbackUri          callback-uri
                  :notificationTypes    ["AppInstNotification"
                                         "AppLcmOpOccNotification"]
                  :source               :nuvla-recovered
                  :created              (now-str)})
          (persist-local-runtime-state!)
          (get-in @mepm-state [:subscriptions subscription-id]))))))

(defn- resume-backed-operation-watchers!
  []
  (when (empty? (:subscriptions @mepm-state))
    (try
      (recover-lifecycle-subscription-from-nuvla!)
      (catch Exception e
        (log/warn e "Failed to recover mock MEPM subscription from Nuvla"))))
  (when (empty? (:app-instances @mepm-state))
    (try
      (recover-app-instances-from-nuvla!)
      (catch Exception e
        (log/warn e "Failed to recover mock MEPM app instances from Nuvla"))))
  (when (= "NUVLA_BACKED" (:backend-mode @mepm-state))
    (doseq [[operation-id operation] (:operations @mepm-state)
            :when (and (processing-operation? operation)
                       (nil? (get @operation-watchers operation-id)))]
      (log/info "Resuming mock MEPM watcher for operation" operation-id)
      (watch-backed-operation! operation-id))))

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
      (persist-local-runtime-state!)
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
        (let [response (emit-notification-to-callback! callback-uri notification)]
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
          op-id    (str "op-" (java.util.UUID/randomUUID))
          backed?  (= "NUVLA_BACKED" (:backend-mode @mepm-state))]
      (try
        (let [backed-result (when backed?
                              (nuvla-backed-mepm/create-backing-deployment! (:mepm-id @mepm-state) app-desc))
              app-id        (or (:mec-backing-deployment-id backed-result)
                                (str "app-" (java.util.UUID/randomUUID)))
              instance      (cond-> {:id                 app-id
                                     :name               (:name app-desc)
                                     :created            (now-str)
                                     :backendMode        (:backend-mode @mepm-state)
                                     :descriptor         app-desc
                                     :instantiationState (if backed?
                                                           "NOT_INSTANTIATED"
                                                           "INSTANTIATED")}
                              (not backed?) (assoc :operationalState "STARTED"))
              operation     (cond-> {:id             op-id
                                     :operationType  "INSTANTIATE"
                                     :appInstanceId  app-id
                                     :status         (if backed? "PROCESSING" "COMPLETED")
                                     :operationState (if backed? "PROCESSING" "COMPLETED")
                                     :stateEnteredTime (now-str)}
                              backed? (assoc :backingDeploymentId app-id))]
          (swap! mepm-state assoc-in [:app-instances app-id] instance)
          (swap! mepm-state assoc-in [:operations op-id] operation)
          (if backed?
            (watch-backed-operation! op-id)
            (when-let [outcome (mock-terminal-outcome operation)]
              (schedule-notification-to-subscribers!
                (fn [subscription-id]
                  (cond-> {:notificationType "AppLcmOpOccNotification"
                           :subscriptionId   subscription-id
                           :operationId      op-id
                           :appInstanceId    app-id
                           :operationType    "INSTANTIATE"
                           :operationState   (:operationState outcome)}
                    (:instantiationState outcome) (assoc :instantiationState (:instantiationState outcome))
                    (contains? outcome :operationalState) (assoc :operationalState (:operationalState outcome)))))))
          {:status 201
           :body   (assoc instance :operationId op-id)})
        (catch clojure.lang.ExceptionInfo e
          {:status (or (:status (ex-data e)) 500)
           :body   {:error "MEPM instantiate failed"
                    :message (ex-message e)
                    :details (ex-data e)}})
        (catch Exception e
          {:status 500
           :body   {:error "MEPM instantiate failed"
                    :message (.getMessage e)}})))))

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
      (try
        (if (= "NUVLA_BACKED" (:backend-mode @mepm-state))
          (let [result    (nuvla-backed-mepm/terminate-backing-deployment! (:mepm-id @mepm-state)
                                                                           {:appInstanceId app-id})
                op-id     (str "op-" (java.util.UUID/randomUUID))
                operation {:id                op-id
                           :operationType     "TERMINATE"
                           :appInstanceId     app-id
                           :status            (if (= "delete" (:action result)) "COMPLETED" "PROCESSING")
                           :operationState    (if (= "delete" (:action result)) "COMPLETED" "PROCESSING")
                           :stateEnteredTime  (now-str)
                           :backingDeploymentId (or (:backing-deployment-id result) app-id)}]
            (swap! mepm-state assoc-in [:operations op-id] operation)
            (if (= "delete" (:action result))
              (let [outcome {:operationState "COMPLETED"
                             :instantiationState "NOT_INSTANTIATED"}]
                (apply-backed-operation-outcome! operation outcome)
                (emit-operation-outcome-notification! operation outcome))
              (watch-backed-operation! op-id))
            {:status 202
             :body   {:operationId op-id
                      :appInstanceId app-id}})
          (let [op-id      (str "op-" (java.util.UUID/randomUUID))
                operation  {:id               op-id
                            :operationType    "TERMINATE"
                            :appInstanceId    app-id
                            :status           "COMPLETED"
                            :operationState   "COMPLETED"
                            :stateEnteredTime (now-str)}
                outcome    (mock-terminal-outcome operation)]
            (swap! mepm-state assoc-in [:operations op-id] operation)
            (apply-backed-operation-outcome! operation outcome)
            (schedule-notification-to-subscribers!
              (fn [subscription-id]
                {:notificationType   "AppLcmOpOccNotification"
                 :subscriptionId     subscription-id
                 :operationId        op-id
                 :appInstanceId      app-id
                 :operationType      "TERMINATE"
                 :operationState     "COMPLETED"
                 :instantiationState "NOT_INSTANTIATED"}))
            {:status 202
             :body   {:operationId op-id
                      :appInstanceId app-id}}))
        (catch clojure.lang.ExceptionInfo e
          {:status (or (:status (ex-data e)) 500)
           :body   {:error "MEPM terminate failed"
                    :message (ex-message e)
                    :details (ex-data e)}})
        (catch Exception e
          {:status 500
           :body   {:error "MEPM terminate failed"
                    :message (.getMessage e)}}))
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
        (try
          (let [backed?       (= "NUVLA_BACKED" (:backend-mode @mepm-state))
                backed-result (when backed?
                                (nuvla-backed-mepm/operate-backing-deployment! (:mepm-id @mepm-state)
                                                                               {:appInstanceId app-id
                                                                                :changeStateTo change-state-to}))
                op-id         (str "op-" (java.util.UUID/randomUUID))
                response-body (assoc instance
                                     :instantiationState "INSTANTIATED"
                                     :operationalState change-state-to
                                     :operationId op-id)
                operation     (cond-> {:id             op-id
                                       :operationType  "OPERATE"
                                       :appInstanceId  app-id
                                       :status         (if backed? "PROCESSING" "COMPLETED")
                                       :operationState (if backed? "PROCESSING" "COMPLETED")
                                       :targetState    change-state-to
                                       :stateEnteredTime (now-str)}
                                backed? (assoc :backingDeploymentId (or (:backing-deployment-id backed-result)
                                                                        app-id)))]
            (when-not backed?
              (let [updated-instance (assoc instance
                                            :instantiationState "INSTANTIATED"
                                            :operationalState change-state-to)]
                (swap! mepm-state assoc-in [:app-instances app-id] updated-instance)))
            (swap! mepm-state assoc-in [:operations op-id] operation)
            (if backed?
              (watch-backed-operation! op-id)
              (when-let [outcome (mock-terminal-outcome operation)]
                (schedule-notification-to-subscribers!
                  (fn [subscription-id]
                    {:notificationType   "AppLcmOpOccNotification"
                     :subscriptionId     subscription-id
                     :operationId        op-id
                     :appInstanceId      app-id
                     :operationType      "OPERATE"
                     :operationState     "COMPLETED"
                     :instantiationState "INSTANTIATED"
                     :operationalState   (:operationalState outcome)}))))
            {:status 200
             :body   response-body})
          (catch clojure.lang.ExceptionInfo e
            {:status (or (:status (ex-data e)) 500)
             :body   {:error "MEPM operate failed"
                      :message (ex-message e)
                      :details (ex-data e)}})
          (catch Exception e
            {:status 500
             :body   {:error "MEPM operate failed"
                      :message (.getMessage e)}}))))))

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
   (log/info "Starting mock MEPM server on port" port)
   (swap! mepm-state assoc :server-port port)
   (restore-local-runtime-state! port)
   (let [server-instance (jetty/run-jetty
                          (create-handler)
                          (merge {:port  port
                                  :join? false}
                                 options))]
     (reset! server server-instance)
     (start-app-instance-reconciler!)
     (resume-backed-operation-watchers!)
     (log/info "Mock MEPM server started on port" port)
     server-instance)))

(defn stop-server!
  "Stop mock MEPM server."
  []
  (cancel-operation-watchers!)
  (cancel-app-instance-reconciler!)
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
