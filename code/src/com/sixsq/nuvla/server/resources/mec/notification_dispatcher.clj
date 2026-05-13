(ns com.sixsq.nuvla.server.resources.mec.notification-dispatcher
  "MEC 010-2 Notification Dispatcher
   
   Dispatches lifecycle notifications to subscribers via HTTP webhooks.
   Monitors app instance and operation occurrence state changes and sends
   notifications to matching subscriptions.
   
   Features:
   - Event-driven notification dispatch
   - Subscription filter matching
   - HTTP webhook delivery with retries
   - Failure tracking and logging
   - Async non-blocking delivery"
  (:require
    [clj-http.client :as http]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
    [com.sixsq.nuvla.server.resources.mec.app-package-subscription :as package-subscription]
    [jsonista.core :as json]))


;;
;; Configuration
;;

(def ^:private default-timeout-ms
  "Default HTTP timeout for webhook delivery"
  30000)

(def ^:private default-connect-timeout-ms
  "Default HTTP connection timeout"
  10000)

(def ^:private default-retry-attempts
  "Default number of retry attempts"
  3)

(def ^:private retry-delay-ms
  "Delay between retry attempts in milliseconds"
  2000)

(def ^:private max-retry-delay-ms
  "Maximum retry delay (exponential backoff cap)"
  30000)


;;
;; Delivery Statistics
;;

(def delivery-stats (atom {:total-sent 0
                            :successful 0
                            :failed     0
                            :retries    0}))


(defn get-delivery-stats
  "Get current delivery statistics.
   
   Returns:
   Map with :total-sent, :successful, :failed, :retries counts"
  []
  @delivery-stats)


(defn reset-delivery-stats!
  "Reset delivery statistics to zero"
  []
  (reset! delivery-stats {:total-sent 0
                          :successful 0
                          :failed     0
                          :retries    0}))


;;
;; HTTP Client
;;

(defn- build-webhook-request
  "Build HTTP request for webhook delivery"
  [callback-uri notification]
  {:url             callback-uri
   :method          :post
   :content-type    :json
   :accept          :json
   :body            (json/write-value-as-string notification)
   :socket-timeout  default-timeout-ms
   :conn-timeout    default-connect-timeout-ms
   :throw-exceptions false})


(defn- calculate-retry-delay
  "Calculate retry delay with exponential backoff"
  [attempt]
  (let [base-delay retry-delay-ms
        exponential-delay (* base-delay (Math/pow 2 (dec attempt)))]
    (int (min max-retry-delay-ms exponential-delay))))


(defn- deliver-webhook
  "Deliver notification via HTTP POST to callback URI.
   
   Parameters:
   - callback-uri: Target webhook URL
   - notification: Notification payload
   - attempt: Current retry attempt (1-based)
   
   Returns:
   - {:success? true :status <http-status>} on success
   - {:success? false :error <error> :status <http-status>} on failure"
  [callback-uri notification attempt]
  (try
    (let [request  (build-webhook-request callback-uri notification)
          response (http/request request)
          status   (:status response)]
      
      (if (and (>= status 200) (< status 300))
        (do
          (log/info "Webhook delivered successfully to" callback-uri
                    "- Status:" status
                    "- Attempt:" attempt)
          {:success? true
           :status   status})
        (do
          (log/warn "Webhook delivery failed to" callback-uri
                    "- Status:" status
                    "- Attempt:" attempt)
          {:success? false
           :status   status
           :error    :http-error
           :message  (str "HTTP " status)})))
    
    (catch java.net.ConnectException e
      (log/warn "Connection failed to" callback-uri "- Attempt:" attempt
                "- Error:" (.getMessage e))
      {:success? false
       :error    :connection-error
       :message  (.getMessage e)})
    
    (catch java.net.SocketTimeoutException e
      (log/warn "Timeout delivering to" callback-uri "- Attempt:" attempt)
      {:success? false
       :error    :timeout
       :message  "Connection timeout"})
    
    (catch Exception e
      (log/error e "Unexpected error delivering webhook to" callback-uri
                 "- Attempt:" attempt)
      {:success? false
       :error    :unexpected-error
       :message  (.getMessage e)})))


(defn- deliver-with-retries
  "Deliver notification with retry logic.
   
   Parameters:
   - callback-uri: Target webhook URL
   - notification: Notification payload
   - max-attempts: Maximum retry attempts
   
   Returns:
   Final delivery result after all attempts"
  [callback-uri notification max-attempts]
  (loop [attempt 1]
    (let [result (deliver-webhook callback-uri notification attempt)]
      (cond
        ;; Success - return immediately
        (:success? result)
        result
        
        ;; Failed but can retry
        (< attempt max-attempts)
        (do
          (swap! delivery-stats update :retries inc)
          (let [delay (calculate-retry-delay attempt)]
            (log/info "Retrying webhook delivery to" callback-uri
                      "in" delay "ms - Attempt" (inc attempt) "of" max-attempts)
            (Thread/sleep delay)
            (recur (inc attempt))))
        
        ;; Failed with no retries left
        :else
        (do
          (log/error "Webhook delivery failed after" max-attempts "attempts to" callback-uri)
          result)))))


;;
;; Notification Dispatch
;;

(defn dispatch-notification
  "Dispatch notification to a single subscription.
   
   Parameters:
   - subscription: Subscription resource
   - notification: Notification payload
   
   Returns:
   Delivery result map"
  [subscription notification]
  (let [callback-uri (:callback-uri subscription)
        sub-id       (:id subscription)]
    
    (log/info "Dispatching" (:notification-type notification)
              "to subscription" sub-id
              "- Callback:" callback-uri)
    
    (swap! delivery-stats update :total-sent inc)
    
    (let [result (deliver-with-retries callback-uri notification default-retry-attempts)]
      (if (:success? result)
        (swap! delivery-stats update :successful inc)
        (swap! delivery-stats update :failed inc))
      
      result)))


(defn dispatch-notification-async
  "Dispatch notification asynchronously (non-blocking).
   
   Parameters:
   - subscription: Subscription resource
   - notification: Notification payload
   
   Returns:
   Future that will contain the delivery result"
  [subscription notification]
  (future
    (dispatch-notification subscription notification)))


;;
;; Event Handling
;;

(defn- active-subscriptions
  [resource-type resource->api subscription-type]
  (let [[_ resources] (crud/query-as-admin resource-type {:last 1000})]
    (->> resources
         (filter :active)
         (map resource->api)
         (filter #(= subscription-type (:subscription-type %)))
         vec)))

(defn- normalize-app-instance
  [app-instance]
  (cond-> {:id                  (or (:id app-instance)
                                    (:appInstanceId app-instance))
           :app-name            (:app-name app-instance)
           :app-d-id            (or (:app-d-id app-instance)
                                    (:appDId app-instance))
           :instantiation-state (or (:instantiation-state app-instance)
                                    (:instantiationState app-instance))
           :operational-state   (or (:operational-state app-instance)
                                    (:operationalState app-instance))}
    (nil? (:app-name app-instance))
    (assoc :app-name (:appName app-instance))))

(defn- normalize-app-lcm-op-occ
  [app-lcm-op-occ]
  {:id                 (or (:id app-lcm-op-occ)
                           (:lcmOpOccId app-lcm-op-occ))
   :app-instance-id    (or (:app-instance-id app-lcm-op-occ)
                           (:appInstanceId app-lcm-op-occ))
   :operation-type     (or (:operation-type app-lcm-op-occ)
                           (:operationType app-lcm-op-occ))
   :operation-state    (or (:operation-state app-lcm-op-occ)
                           (:operationState app-lcm-op-occ))
   :start-time         (or (:start-time app-lcm-op-occ)
                           (:startTime app-lcm-op-occ))
   :state-entered-time (or (:state-entered-time app-lcm-op-occ)
                           (:stateEnteredTime app-lcm-op-occ))})

(defn- normalize-app-package
  [app-pkg]
  {:app-pkg-id        (or (:app-pkg-id app-pkg)
                          (:appPkgId app-pkg)
                          (:id app-pkg))
   :app-d-id          (or (:app-d-id app-pkg)
                          (:appDId app-pkg))
   :app-name          (or (:app-name app-pkg)
                          (:appName app-pkg))
   :operational-state (or (:operational-state app-pkg)
                          (:operationalState app-pkg))
   :onboarding-state  (or (:onboarding-state app-pkg)
                          (:onboardingState app-pkg))})

(defn handle-app-instance-state-change
  "Handle app instance state change event.
   
   Finds matching subscriptions and dispatches notifications.
   
   Parameters:
   - subscriptions: Collection of all subscriptions
   - app-instance: Current app instance state
   - change-type: Type of change (INSTANTIATION_STATE, OPERATIONAL_STATE, CONFIGURATION)
   - previous-state: Previous state before change
   
   Returns:
   Vector of dispatch futures"
  [subscriptions app-instance change-type previous-state]
  (let [active-subs (subscription/get-active-subscriptions-for-type
                      subscriptions
                      subscription/app-instance-subscription-type)
        
        matching-subs (filter #(subscription/matches-app-instance-filter? % app-instance)
                              active-subs)]
    
    (log/info "App instance" (:id app-instance) "state changed -"
              change-type "- Matching subscriptions:" (count matching-subs))
    
    (mapv (fn [sub]
            (let [notification (subscription/build-app-instance-notification
                                 sub
                                 app-instance
                                 change-type
                                 previous-state)]
              (dispatch-notification-async sub notification)))
          matching-subs)))


(defn handle-app-lcm-op-occ-state-change
  "Handle app LCM operation occurrence state change event.
   
   Finds matching subscriptions and dispatches notifications.
   
   Parameters:
   - subscriptions: Collection of all subscriptions
   - app-lcm-op-occ: Current operation occurrence state
   - change-type: Type of change (OPERATION_STATE, OPERATION_RESULT)
   - previous-state: Previous state before change
   
   Returns:
   Vector of dispatch futures"
  [subscriptions app-lcm-op-occ change-type previous-state]
  (let [active-subs (subscription/get-active-subscriptions-for-type
                      subscriptions
                      subscription/app-lcm-op-occ-subscription-type)
        
        matching-subs (filter #(subscription/matches-app-lcm-op-occ-filter? % app-lcm-op-occ)
                              active-subs)]
    
    (log/info "Operation" (:id app-lcm-op-occ) "state changed -"
              change-type "- Matching subscriptions:" (count matching-subs))
    
    (mapv (fn [sub]
            (let [notification (subscription/build-app-lcm-op-occ-notification
                                 sub
                                 app-lcm-op-occ
                                 change-type
                                 previous-state)]
              (dispatch-notification-async sub notification)))
          matching-subs)))


(defn dispatch-app-instance-state-change!
  "Load durable subscriptions and dispatch app instance notifications.
   Best effort only: dispatch failures are logged and do not propagate."
  [app-instance change-type previous-state]
  (try
    (handle-app-instance-state-change (active-subscriptions subscription/resource-type
                                                            subscription/resource->api-subscription
                                                            subscription/app-instance-subscription-type)
                                      (normalize-app-instance app-instance)
                                      change-type
                                      previous-state)
    (catch Exception e
      (log/error e "Failed to dispatch app instance notifications")
      [])))


(defn dispatch-app-lcm-op-occ-state-change!
  "Load durable subscriptions and dispatch operation-occurrence notifications.
   Best effort only: dispatch failures are logged and do not propagate."
  [app-lcm-op-occ change-type previous-state]
  (try
    (handle-app-lcm-op-occ-state-change (active-subscriptions subscription/resource-type
                                                              subscription/resource->api-subscription
                                                              subscription/app-lcm-op-occ-subscription-type)
                                        (normalize-app-lcm-op-occ app-lcm-op-occ)
                                        change-type
                                        previous-state)
    (catch Exception e
      (log/error e "Failed to dispatch operation occurrence notifications")
      [])))

(defn handle-app-package-onboarding
  "Handle app package onboarding events."
  [subscriptions app-pkg]
  (let [active-subs   (package-subscription/get-active-subscriptions-for-type
                        subscriptions
                        "AppPackageOnBoardingNotification")
        matching-subs (filter #(package-subscription/matches-app-pkg-filter? % app-pkg)
                              active-subs)]
    (log/info "App package" (:app-pkg-id app-pkg) "onboarded - Matching subscriptions:" (count matching-subs))
    (mapv (fn [sub]
            (let [notification (package-subscription/build-app-package-onboarding-notification
                                 sub
                                 app-pkg)]
              (dispatch-notification-async sub notification)))
          matching-subs)))

(defn handle-app-package-state-change
  "Handle app package state-change events."
  [subscriptions app-pkg change-type previous-state]
  (let [active-subs   (package-subscription/get-active-subscriptions-for-type
                        subscriptions
                        "AppPackageStateChangeNotification")
        matching-subs (filter #(package-subscription/matches-app-pkg-filter? % app-pkg)
                              active-subs)]
    (log/info "App package" (:app-pkg-id app-pkg) "state changed -" change-type
              "- Matching subscriptions:" (count matching-subs))
    (mapv (fn [sub]
            (let [notification (package-subscription/build-app-package-state-change-notification
                                 sub
                                 app-pkg
                                 change-type
                                 previous-state)]
              (dispatch-notification-async sub notification)))
          matching-subs)))

(defn dispatch-app-package-onboarding!
  "Load durable subscriptions and dispatch app package onboarding notifications.
   Best effort only: dispatch failures are logged and do not propagate."
  [app-pkg]
  (try
    (handle-app-package-onboarding (active-subscriptions package-subscription/resource-type
                                                         package-subscription/resource->api-subscription
                                                         "AppPackageOnBoardingNotification")
                                   (normalize-app-package app-pkg))
    (catch Exception e
      (log/error e "Failed to dispatch app package onboarding notifications")
      [])))

(defn dispatch-app-package-state-change!
  "Load durable subscriptions and dispatch app package state-change notifications.
   Best effort only: dispatch failures are logged and do not propagate."
  [app-pkg change-type previous-state]
  (try
    (handle-app-package-state-change (active-subscriptions package-subscription/resource-type
                                                           package-subscription/resource->api-subscription
                                                           "AppPackageStateChangeNotification")
                                     (normalize-app-package app-pkg)
                                     change-type
                                     previous-state)
    (catch Exception e
      (log/error e "Failed to dispatch app package state-change notifications")
      [])))


;;
;; Kafka Event Integration (Stub)
;;

(defn start-event-listener
  "Start listening to Kafka events for lifecycle changes.
   
   This is a stub for future Kafka integration. In production, this would:
   1. Subscribe to relevant Kafka topics (deployment events, job events)
   2. Parse events and detect state changes
   3. Call handle-app-instance-state-change or handle-app-lcm-op-occ-state-change
   4. Log event processing statistics
   
   Parameters:
   - subscription-store: Atom containing subscription collection
   - opts: Configuration options
     * :kafka-brokers - Kafka broker addresses
     * :topics - Topics to subscribe to
     * :group-id - Consumer group ID
   
   Returns:
   Event listener handle (for stopping)"
  [subscription-store opts]
  (log/info "Starting MEC notification event listener (stub)")
  (log/info "Kafka configuration:" (select-keys opts [:kafka-brokers :topics :group-id]))
  
  ;; TODO: Implement actual Kafka consumer
  ;; For now, return a stub handle
  {:type :stub
   :started-at (java.time.Instant/now)
   :subscription-store subscription-store
   :opts opts})


(defn stop-event-listener
  "Stop event listener and clean up resources.
   
   Parameters:
   - listener-handle: Handle returned from start-event-listener
   
   Returns:
   nil"
  [listener-handle]
  (log/info "Stopping MEC notification event listener")
  (when (= (:type listener-handle) :stub)
    (log/info "Stub listener stopped"))
  nil)


;;
;; Manual Event Triggering (for testing)
;;

(defn trigger-app-instance-notification
  "Manually trigger app instance notification (for testing).
   
   Parameters:
   - subscriptions: Collection of subscriptions
   - app-instance: App instance resource
   - change-type: Type of change
   - previous-state: Previous state (optional)
   
   Returns:
   Vector of delivery futures"
  [subscriptions app-instance change-type previous-state]
  (log/info "Manually triggering app instance notification")
  (handle-app-instance-state-change subscriptions app-instance change-type previous-state))


(defn trigger-app-lcm-op-occ-notification
  "Manually trigger operation occurrence notification (for testing).
   
   Parameters:
   - subscriptions: Collection of subscriptions
   - app-lcm-op-occ: Operation occurrence resource
   - change-type: Type of change
   - previous-state: Previous state (optional)
   
   Returns:
   Vector of delivery futures"
  [subscriptions app-lcm-op-occ change-type previous-state]
  (log/info "Manually triggering operation occurrence notification")
  (handle-app-lcm-op-occ-state-change subscriptions app-lcm-op-occ change-type previous-state))
