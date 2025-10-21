(ns com.sixsq.nuvla.server.resources.mec.app-lcm-subscription-test
  "Tests for MEC 010-2 Application Lifecycle Subscription"
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]))


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
;; Subscription Creation Tests
;;

(deftest test-create-subscription-app-instance
  (testing "Create AppInstanceStateChangeNotification subscription"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {:app-name "test-app"
                 :operational-state "STARTED"}
                test-user-id)]
      
      (is (string? (:id sub)))
      (is (.startsWith (:id sub) "subscription/"))
      (is (= "AppInstanceStateChangeNotification" (:subscription-type sub)))
      (is (= "https://example.com/webhook" (:callback-uri sub)))
      (is (= {:app-name "test-app"
              :operational-state "STARTED"}
             (:app-instance-filter sub)))
      (is (= test-user-id (:owner sub)))
      (is (true? (:active sub)))
      (is (some? (:created sub)))
      (is (some? (:updated sub))))))


(deftest test-create-subscription-app-lcm-op-occ
  (testing "Create AppLcmOpOccStateChangeNotification subscription"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "https://example.com/webhook"
                {:operation-type "INSTANTIATE"
                 :operation-state "COMPLETED"}
                test-user-id)]
      
      (is (string? (:id sub)))
      (is (= "AppLcmOpOccStateChangeNotification" (:subscription-type sub)))
      (is (= {:operation-type "INSTANTIATE"
              :operation-state "COMPLETED"}
             (:app-lcm-op-occ-filter sub)))
      (is (nil? (:app-instance-filter sub))))))


(deftest test-create-subscription-no-filter
  (testing "Create subscription without filter (match all)"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)]
      
      (is (nil? (:app-instance-filter sub)))
      (is (= "AppInstanceStateChangeNotification" (:subscription-type sub))))))


;;
;; Validation Tests
;;

(deftest test-validate-subscription-valid
  (testing "Validate valid subscription"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)
          result (subscription/validate-subscription sub)]
      
      (is (true? (:valid? result)))
      (is (nil? (:errors result))))))


(deftest test-validate-subscription-invalid-callback-uri
  (testing "Validate subscription with invalid callback URI"
    (let [sub {:id                "subscription/test"
               :subscription-type "AppInstanceStateChangeNotification"
               :callback-uri      "not-a-uri"}
          result (subscription/validate-subscription sub)]
      
      (is (false? (:valid? result)))
      (is (some? (:errors result))))))


;;
;; Update Tests
;;

(deftest test-update-subscription
  (testing "Update subscription callback URI and filter"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {:app-name "test-app"}
                test-user-id)
          updated-sub (subscription/update-subscription
                        sub
                        {:callback-uri "https://example.com/new-webhook"
                         :app-instance-filter {:app-name "new-app"}
                         :active false})]
      
      (is (= "https://example.com/new-webhook" (:callback-uri updated-sub)))
      (is (= {:app-name "new-app"} (:app-instance-filter updated-sub)))
      (is (false? (:active updated-sub)))
      (is (= (:id sub) (:id updated-sub)))
      (is (= (:created sub) (:created updated-sub)))
      (is (not= (:updated sub) (:updated updated-sub))))))


(deftest test-deactivate-subscription
  (testing "Deactivate subscription (soft delete)"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)
          deactivated (subscription/deactivate-subscription sub)]
      
      (is (false? (:active deactivated)))
      (is (not= (:updated sub) (:updated deactivated))))))


;;
;; Filter Matching Tests
;;

(deftest test-matches-app-instance-filter-no-filter
  (testing "Empty filter matches all app instances"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)]
      
      (is (true? (subscription/matches-app-instance-filter? sub test-app-instance))))))


(deftest test-matches-app-instance-filter-exact-match
  (testing "Filter matches app instance exactly"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {:app-name "test-app"
                 :operational-state "STARTED"}
                test-user-id)]
      
      (is (true? (subscription/matches-app-instance-filter? sub test-app-instance))))))


(deftest test-matches-app-instance-filter-no-match
  (testing "Filter does not match app instance"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {:app-name "different-app"}
                test-user-id)]
      
      (is (false? (subscription/matches-app-instance-filter? sub test-app-instance))))))


(deftest test-matches-app-instance-filter-partial-match
  (testing "Partial filter matches (only some fields specified)"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {:operational-state "STARTED"}
                test-user-id)]
      
      (is (true? (subscription/matches-app-instance-filter? sub test-app-instance))))))


(deftest test-matches-app-lcm-op-occ-filter-match
  (testing "Filter matches operation occurrence"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "https://example.com/webhook"
                {:operation-type "INSTANTIATE"
                 :operation-state "COMPLETED"}
                test-user-id)]
      
      (is (true? (subscription/matches-app-lcm-op-occ-filter? sub test-app-lcm-op-occ))))))


(deftest test-matches-app-lcm-op-occ-filter-no-match
  (testing "Filter does not match operation occurrence"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "https://example.com/webhook"
                {:operation-type "TERMINATE"}
                test-user-id)]
      
      (is (false? (subscription/matches-app-lcm-op-occ-filter? sub test-app-lcm-op-occ))))))


;;
;; Notification Building Tests
;;

(deftest test-build-app-instance-notification
  (testing "Build AppInstanceStateChangeNotification"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)
          notification (subscription/build-app-instance-notification
                         sub
                         test-app-instance
                         "OPERATIONAL_STATE"
                         "STOPPED")]
      
      (is (= "AppInstanceStateChangeNotification" (:notification-type notification)))
      (is (string? (:notification-id notification)))
      (is (.startsWith (:notification-id notification) "notification/"))
      (is (= (:id sub) (:subscription-id notification)))
      (is (= "deployment/abc-123" (:app-instance-id notification)))
      (is (= "test-app" (:app-name notification)))
      (is (= "STARTED" (:operational-state notification)))
      (is (= "OPERATIONAL_STATE" (:change-type notification)))
      (is (= "STOPPED" (:previous-state notification)))
      (is (some? (:timestamp notification)))
      (is (map? (:_links notification)))
      (is (some? (get-in notification [:_links :subscription :href])))
      (is (some? (get-in notification [:_links :app-instance :href]))))))


(deftest test-build-app-lcm-op-occ-notification
  (testing "Build AppLcmOpOccStateChangeNotification"
    (let [sub (subscription/create-subscription
                "AppLcmOpOccStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)
          notification (subscription/build-app-lcm-op-occ-notification
                         sub
                         test-app-lcm-op-occ
                         "OPERATION_STATE"
                         "PROCESSING")]
      
      (is (= "AppLcmOpOccStateChangeNotification" (:notification-type notification)))
      (is (string? (:notification-id notification)))
      (is (= (:id sub) (:subscription-id notification)))
      (is (= "job/op-123" (:app-lcm-op-occ-id notification)))
      (is (= "deployment/abc-123" (:app-instance-id notification)))
      (is (= "INSTANTIATE" (:operation-type notification)))
      (is (= "COMPLETED" (:operation-state notification)))
      (is (= "OPERATION_STATE" (:change-type notification)))
      (is (= "PROCESSING" (:previous-state notification)))
      (is (= (java.time.Instant/parse "2025-01-01T10:00:00Z") (:start-time notification)))
      (is (some? (:timestamp notification)))
      (is (map? (:_links notification)))
      (is (some? (get-in notification [:_links :subscription :href])))
      (is (some? (get-in notification [:_links :app-lcm-op-occ :href])))
      (is (some? (get-in notification [:_links :app-instance :href]))))))


;;
;; Query Tests
;;

(deftest test-query-subscriptions-no-filter
  (testing "Query all subscriptions"
    (let [sub1 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "https://example.com/webhook1"
                 {}
                 test-user-id)
          sub2 (subscription/create-subscription
                 "AppLcmOpOccStateChangeNotification"
                 "https://example.com/webhook2"
                 {}
                 test-user-id)
          subs [sub1 sub2]
          result (subscription/query-subscriptions subs {})]
      
      (is (= 2 (count result)))
      (is (some #(= (:id %) (:id sub1)) result))
      (is (some #(= (:id %) (:id sub2)) result)))))


(deftest test-query-subscriptions-by-type
  (testing "Query subscriptions by type"
    (let [sub1 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "https://example.com/webhook1"
                 {}
                 test-user-id)
          sub2 (subscription/create-subscription
                 "AppLcmOpOccStateChangeNotification"
                 "https://example.com/webhook2"
                 {}
                 test-user-id)
          subs [sub1 sub2]
          result (subscription/query-subscriptions
                   subs
                   {:subscription-type "AppInstanceStateChangeNotification"})]
      
      (is (= 1 (count result)))
      (is (= (:id sub1) (:id (first result)))))))


(deftest test-query-subscriptions-by-owner
  (testing "Query subscriptions by owner"
    (let [sub1 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "https://example.com/webhook1"
                 {}
                 "user/owner1")
          sub2 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "https://example.com/webhook2"
                 {}
                 "user/owner2")
          subs [sub1 sub2]
          result (subscription/query-subscriptions subs {:owner "user/owner1"})]
      
      (is (= 1 (count result)))
      (is (= (:id sub1) (:id (first result)))))))


(deftest test-query-subscriptions-pagination
  (testing "Query subscriptions with pagination"
    (let [subs (mapv #(subscription/create-subscription
                        "AppInstanceStateChangeNotification"
                        (str "https://example.com/webhook" %)
                        {}
                        test-user-id)
                     (range 10))
          result1 (subscription/query-subscriptions subs {:limit 5 :offset 0})
          result2 (subscription/query-subscriptions subs {:limit 5 :offset 5})]
      
      (is (= 5 (count result1)))
      (is (= 5 (count result2)))
      (is (not= (map :id result1) (map :id result2))))))


(deftest test-get-subscription-by-id
  (testing "Get subscription by ID"
    (let [sub (subscription/create-subscription
                "AppInstanceStateChangeNotification"
                "https://example.com/webhook"
                {}
                test-user-id)
          subs [sub]
          result (subscription/get-subscription-by-id subs (:id sub))]
      
      (is (= (:id sub) (:id result)))
      (is (= (:callback-uri sub) (:callback-uri result))))))


(deftest test-get-subscription-by-id-not-found
  (testing "Get subscription by ID - not found"
    (let [subs []
          result (subscription/get-subscription-by-id subs "subscription/nonexistent")]
      
      (is (nil? result)))))


(deftest test-get-active-subscriptions-for-type
  (testing "Get active subscriptions for specific type"
    (let [sub1 (subscription/create-subscription
                 "AppInstanceStateChangeNotification"
                 "https://example.com/webhook1"
                 {}
                 test-user-id)
          sub2 (subscription/create-subscription
                 "AppLcmOpOccStateChangeNotification"
                 "https://example.com/webhook2"
                 {}
                 test-user-id)
          sub3 (subscription/deactivate-subscription
                 (subscription/create-subscription
                   "AppInstanceStateChangeNotification"
                   "https://example.com/webhook3"
                   {}
                   test-user-id))
          subs [sub1 sub2 sub3]
          result (subscription/get-active-subscriptions-for-type
                   subs
                   "AppInstanceStateChangeNotification")]
      
      (is (= 1 (count result)))
      (is (= (:id sub1) (:id (first result)))))))


;;
;; Module Completeness Test
;;

(deftest test-subscription-module-complete
  (testing "Verify all expected functions are available"
    (let [expected-fns ['create-subscription
                        'validate-subscription
                        'update-subscription
                        'deactivate-subscription
                        'matches-app-instance-filter?
                        'matches-app-lcm-op-occ-filter?
                        'build-app-instance-notification
                        'build-app-lcm-op-occ-notification
                        'query-subscriptions
                        'get-subscription-by-id
                        'get-active-subscriptions-for-type]]
      (doseq [fn-name expected-fns]
        (is (some? (ns-resolve 'com.sixsq.nuvla.server.resources.mec.app-lcm-subscription fn-name))
            (str "Function " fn-name " should be defined"))))))
