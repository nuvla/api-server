(ns com.sixsq.nuvla.server.resources.mec.notification-dispatcher-test
  "Tests for MEC 010-2 Notification Dispatcher"
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
    [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]))


;;
;; Test Helpers
;;

(def delivered-notifications (atom []))


(defn reset-test-state!
  "Reset test state"
  []
  (reset! delivered-notifications [])
  (dispatcher/reset-delivery-stats!))


(use-fixtures :each
  (fn [f]
    (reset-test-state!)
    (f)))


;;
;; Test Data
;;

(def test-user-id "user/test-user")

(def test-app-instance
  {:id                   "deployment/abc-123"
   :app-name             "test-app"
   :app-d-id             "appd/test-1"
   :instantiation-state  "INSTANTIATED"
   :operational-state    "STARTED"})

(def test-app-lcm-op-occ
  {:id                  "job/op-123"
   :app-instance-id     "deployment/abc-123"
   :operation-type      "INSTANTIATE"
   :operation-state     "COMPLETED"
   :start-time          (java.time.Instant/parse "2025-01-01T10:00:00Z")
   :state-entered-time  (java.time.Instant/parse "2025-01-01T10:05:00Z")})


;;
;; Webhook Delivery Tests (Using invalid endpoints to test error handling)
;;

(deftest test-dispatch-notification-failure
  (testing "Dispatch notification to invalid endpoint fails gracefully"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "http://localhost:99999/invalid"  ; Invalid port
                {}
                test-user-id)
          notification (subscription/build-app-instance-notification
                         sub
                         test-app-instance
                         "OPERATIONAL_STATE"
                         "STOPPED")
          result (dispatcher/dispatch-notification sub notification)]
      
      ;; Should fail but not throw
      (is (false? (:success? result)))
      (is (contains? #{:connection-error :unexpected-error} (:error result)))
      
      ;; Check stats
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 1 (:total-sent stats)))
        (is (= 0 (:successful stats)))
        (is (= 1 (:failed stats)))
        (is (>= (:retries stats) 2)))))) ; At least 2 retries


(deftest test-dispatch-notification-async-returns-future
  (testing "Dispatch notification asynchronously returns future"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "http://localhost:99999/invalid"
                {}
                test-user-id)
          notification (subscription/build-app-instance-notification
                         sub
                         test-app-instance
                         "OPERATIONAL_STATE"
                         "STOPPED")
          future-result (dispatcher/dispatch-notification-async sub notification)]
      
      ;; Future should be created immediately
      (is (future? future-result))
      
      ;; Wait for async delivery (will fail but shouldn't throw)
      (let [result @future-result]
        (is (false? (:success? result)))))))


;;
;; App Instance State Change Tests
;;

(deftest test-handle-app-instance-state-change-no-subscriptions
  (testing "Handle app instance state change with no subscriptions"
    (let [subscriptions []
          futures (dispatcher/handle-app-instance-state-change
                    subscriptions
                    test-app-instance
                    "OPERATIONAL_STATE"
                    "STOPPED")]
      
      ;; No subscriptions = no notifications
      (is (empty? futures)))))


(deftest test-handle-app-instance-state-change-matching-subscription
  (testing "Handle app instance state change with matching subscription"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "http://localhost:99999/webhook"  ; Will fail but that's OK for test
                {:app-name "test-app"}
                test-user-id)
          subscriptions [sub]
          futures (dispatcher/handle-app-instance-state-change
                    subscriptions
                    test-app-instance
                    "OPERATIONAL_STATE"
                    "STOPPED")]
      
      ;; One matching subscription
      (is (= 1 (count futures)))
      (is (every? future? futures))
      
      ;; Wait for delivery
      (doseq [f futures] @f)
      
      ;; Check stats - notification was attempted
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 1 (:total-sent stats)))))))


(deftest test-handle-app-instance-state-change-non-matching-subscription
  (testing "Handle app instance state change with non-matching subscription"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "http://localhost:99999/webhook"
                {:app-name "different-app"}  ; Does not match
                test-user-id)
          subscriptions [sub]
          futures (dispatcher/handle-app-instance-state-change
                    subscriptions
                    test-app-instance
                    "OPERATIONAL_STATE"
                    "STOPPED")]
      
      ;; No matching subscriptions
      (is (empty? futures)))))


(deftest test-handle-app-instance-state-change-inactive-subscription
  (testing "Handle app instance state change with inactive subscription"
    (let [sub (subscription/deactivate-subscription
                (subscription/create-subscription
                  "AppInstanceStateChangeNotification"
                  "http://localhost:99999/webhook"
                  {}
                  test-user-id))
          subscriptions [sub]
          futures (dispatcher/handle-app-instance-state-change
                    subscriptions
                    test-app-instance
                    "OPERATIONAL_STATE"
                    "STOPPED")]
      
      ;; Inactive subscription should not match
      (is (empty? futures)))))


(deftest test-handle-app-instance-state-change-multiple-subscriptions
  (testing "Handle app instance state change with multiple subscriptions"
    (let [sub1 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "http://localhost:99999/webhook1"
                 {:operational-state "STARTED"}
                 test-user-id)
          sub2 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "http://localhost:99999/webhook2"
                 {:app-name "test-app"}
                 test-user-id)
          sub3 (subscription/create-subscription
                 "AppLcmOpOccStateChangeNotification"  ; Wrong type
                 "http://localhost:99999/webhook3"
                 {}
                 test-user-id)
          subscriptions [sub1 sub2 sub3]
          futures (dispatcher/handle-app-instance-state-change
                    subscriptions
                    test-app-instance
                    "OPERATIONAL_STATE"
                    "STOPPED")]
      
      ;; Two matching subscriptions (sub1 and sub2, not sub3)
      (is (= 2 (count futures)))
      
      ;; Wait for delivery
      (doseq [f futures] @f)
      
      ;; Check stats - 2 notifications attempted
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 2 (:total-sent stats)))))))


;;
;; App LCM Op Occ State Change Tests
;;

(deftest test-handle-app-lcm-op-occ-state-change-matching-subscription
  (testing "Handle operation state change with matching subscription"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "http://localhost:99999/webhook"
                {:operation-type "INSTANTIATE"}
                test-user-id)
          subscriptions [sub]
          futures (dispatcher/handle-app-lcm-op-occ-state-change
                    subscriptions
                    test-app-lcm-op-occ
                    "OPERATION_STATE"
                    "PROCESSING")]
      
      ;; One matching subscription
      (is (= 1 (count futures)))
      
      ;; Wait for delivery
      (doseq [f futures] @f)
      
      ;; Check stats - notification attempted
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 1 (:total-sent stats)))))))


(deftest test-handle-app-lcm-op-occ-state-change-filter-by-state
  (testing "Handle operation state change filtered by operation state"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "http://localhost:99999/webhook"
                {:operation-state "FAILED"}  ; Does not match COMPLETED
                test-user-id)
          subscriptions [sub]
          futures (dispatcher/handle-app-lcm-op-occ-state-change
                    subscriptions
                    test-app-lcm-op-occ
                    "OPERATION_STATE"
                    "PROCESSING")]
      
      ;; No matching subscriptions
      (is (empty? futures)))))


;;
;; Manual Triggering Tests
;;

(deftest test-trigger-app-instance-notification
  (testing "Manually trigger app instance notification"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "http://localhost:99999/webhook"
                {}
                test-user-id)
          subscriptions [sub]
          futures (dispatcher/trigger-app-instance-notification
                    subscriptions
                    test-app-instance
                    "OPERATIONAL_STATE"
                    "STOPPED")]
      
      (is (= 1 (count futures)))
      
      ;; Wait for delivery
      (doseq [f futures] @f)
      
      ;; Check stats
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 1 (:total-sent stats)))))))


(deftest test-trigger-app-lcm-op-occ-notification
  (testing "Manually trigger operation occurrence notification"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "http://localhost:99999/webhook"
                {}
                test-user-id)
          subscriptions [sub]
          futures (dispatcher/trigger-app-lcm-op-occ-notification
                    subscriptions
                    test-app-lcm-op-occ
                    "OPERATION_STATE"
                    "PROCESSING")]
      
      (is (= 1 (count futures)))
      
      ;; Wait for delivery
      (doseq [f futures] @f)
      
      ;; Check stats
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 1 (:total-sent stats)))))))


;;
;; Delivery Stats Tests
;;

(deftest test-delivery-stats
  (testing "Track delivery statistics"
    (let [sub-fail (subscription/create-subscription
                     "AppInstanceStateChangeNotification"
                     "http://localhost:99999/invalid"
                     {}
                     test-user-id)
          notification (subscription/build-app-instance-notification
                         sub-fail
                         test-app-instance
                         "OPERATIONAL_STATE"
                         "STOPPED")]
      
      ;; Reset stats
      (dispatcher/reset-delivery-stats!)
      (is (= 0 (:total-sent (dispatcher/get-delivery-stats))))
      
      ;; Failed delivery
      (dispatcher/dispatch-notification sub-fail notification)
      
      ;; Check stats
      (let [stats (dispatcher/get-delivery-stats)]
        (is (= 1 (:total-sent stats)))
        (is (= 0 (:successful stats)))
        (is (= 1 (:failed stats)))
        (is (>= (:retries stats) 2))))))


(deftest test-reset-delivery-stats
  (testing "Reset delivery statistics"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "http://localhost:99999/webhook"
                {}
                test-user-id)
          notification (subscription/build-app-instance-notification
                         sub
                         test-app-instance
                         "OPERATIONAL_STATE"
                         "STOPPED")]
      
      ;; Send notification
      (dispatcher/dispatch-notification sub notification)
      (is (= 1 (:total-sent (dispatcher/get-delivery-stats))))
      
      ;; Reset
      (dispatcher/reset-delivery-stats!)
      (is (= 0 (:total-sent (dispatcher/get-delivery-stats))))
      (is (= 0 (:successful (dispatcher/get-delivery-stats))))
      (is (= 0 (:failed (dispatcher/get-delivery-stats))))
      (is (= 0 (:retries (dispatcher/get-delivery-stats)))))))


;;
;; Event Listener Tests
;;

(deftest test-start-stop-event-listener
  (testing "Start and stop event listener (stub)"
    (let [subscription-store (atom [])
          listener (dispatcher/start-event-listener
                     subscription-store
                     {:kafka-brokers ["localhost:9092"]
                      :topics ["deployment-events" "job-events"]
                      :group-id "mec-notifications"})]
      
      ;; Should return listener handle
      (is (some? listener))
      (is (= :stub (:type listener)))
      (is (some? (:started-at listener)))
      
      ;; Stop listener
      (is (nil? (dispatcher/stop-event-listener listener))))))


;;
;; Module Completeness Test
;;

(deftest test-notification-dispatcher-complete
  (testing "Verify all expected functions are available"
    (let [expected-fns ['dispatch-notification
                        'dispatch-notification-async
                        'handle-app-instance-state-change
                        'handle-app-lcm-op-occ-state-change
                        'start-event-listener
                        'stop-event-listener
                        'trigger-app-instance-notification
                        'trigger-app-lcm-op-occ-notification
                        'get-delivery-stats
                        'reset-delivery-stats!]]
      (doseq [fn-name expected-fns]
        (is (some? (ns-resolve 'com.sixsq.nuvla.server.resources.mec.notification-dispatcher fn-name))
            (str "Function " fn-name " should be defined"))))))
