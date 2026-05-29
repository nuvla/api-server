 (ns com.sixsq.nuvla.server.resources.mec.mock-mepm-server-test
   (:require
     [clj-http.client :as http]
     [clojure.test :refer [deftest is testing]]
     [com.sixsq.nuvla.server.resources.mec.mock-mepm-server :as mock]
     [com.sixsq.nuvla.server.resources.mec.nuvla-backed-mepm :as nuvla-backed-mepm]
     [jsonista.core :as json]))


 (defn- emitted-notification
   [request]
   (some-> (:body request)
           (json/read-value json/keyword-keys-object-mapper)))


 (deftest test-poll-backed-operation-step-failed-instantiate
   (mock/reset-state!)
   (let [requests (atom [])]
     (swap! (var-get #'mock/mepm-state)
            assoc
            :subscriptions {"sub-1" {:id "sub-1"
                                     :callbackUri "http://callback.local/mm3"
                                     :active true}}
            :app-instances {"deployment/backing-1" {:id "deployment/backing-1"
                                                    :instantiationState "NOT_INSTANTIATED"}}
            :operations {"op-1" {:id "op-1"
                                 :operationType "INSTANTIATE"
                                 :appInstanceId "deployment/backing-1"
                                 :status "PROCESSING"
                                 :operationState "PROCESSING"}})
     (with-redefs [nuvla-backed-mepm/retrieve-backing-deployment! (fn [_]
                                                                    {:id "deployment/backing-1"
                                                                     :state "ERROR"
                                                                     :status-message "native deployment failed"})
                   http/post (fn [url request]
                               (swap! requests conj {:url url :request request})
                               {:status 202})]
       (testing "terminal ERROR becomes FAILED op notification"
         (is (= :completed (#'mock/poll-backed-operation-step! "op-1")))
         (is (= "FAILED" (get-in @(var-get #'mock/mepm-state) [:operations "op-1" :operationState])))
         (is (= "NOT_INSTANTIATED" (get-in @(var-get #'mock/mepm-state) [:app-instances "deployment/backing-1" :instantiationState])))
         (is (= "AppLcmOpOccNotification" (:notificationType (emitted-notification (:request (first @requests))))))
         (is (= "FAILED" (:operationState (emitted-notification (:request (first @requests))))))
         (is (= "native deployment failed"
                (get-in (emitted-notification (:request (first @requests))) [:error :detail])))))))


 (deftest test-poll-backed-operation-step-completed-operate-stop
   (mock/reset-state!)
   (let [requests (atom [])]
     (swap! (var-get #'mock/mepm-state)
            assoc
            :subscriptions {"sub-1" {:id "sub-1"
                                     :callbackUri "http://callback.local/mm3"
                                     :active true}}
            :app-instances {"deployment/backing-1" {:id "deployment/backing-1"
                                                    :instantiationState "INSTANTIATED"
                                                    :operationalState "STARTED"}}
            :operations {"op-2" {:id "op-2"
                                 :operationType "OPERATE"
                                 :appInstanceId "deployment/backing-1"
                                 :targetState "STOPPED"
                                 :status "PROCESSING"
                                 :operationState "PROCESSING"}})
     (with-redefs [nuvla-backed-mepm/retrieve-backing-deployment! (fn [_]
                                                                    {:id "deployment/backing-1"
                                                                     :state "STOPPED"})
                   http/post (fn [url request]
                               (swap! requests conj {:url url :request request})
                               {:status 202})]
       (testing "terminal STOPPED completes stop operation"
         (is (= :completed (#'mock/poll-backed-operation-step! "op-2")))
         (is (= "COMPLETED" (get-in @(var-get #'mock/mepm-state) [:operations "op-2" :operationState])))
         (is (= "STOPPED" (get-in @(var-get #'mock/mepm-state) [:app-instances "deployment/backing-1" :operationalState])))
         (is (= "COMPLETED" (:operationState (emitted-notification (:request (first @requests))))))
         (is (= "STOPPED" (:operationalState (emitted-notification (:request (first @requests))))))))))


(deftest test-resume-backed-operation-watchers
  (mock/reset-state!)
  (swap! (var-get #'mock/mepm-state)
         assoc
         :backend-mode "NUVLA_BACKED"
         :operations {"op-1" {:id "op-1"
                              :operationType "INSTANTIATE"
                              :appInstanceId "deployment/backing-1"
                              :status "PROCESSING"
                              :operationState "PROCESSING"}
                      "op-2" {:id "op-2"
                              :operationType "OPERATE"
                              :appInstanceId "deployment/backing-2"
                              :status "COMPLETED"
                              :operationState "COMPLETED"}})
  (let [resumed (atom [])]
    (with-redefs-fn {#'mock/watch-backed-operation! (fn [operation-id]
                                                      (swap! resumed conj operation-id)
                                                      :watching)}
      (fn []
        (testing "restart resumes only processing backed operations"
          (#'mock/resume-backed-operation-watchers!)
          (is (= ["op-1"] @resumed)))))))


(deftest test-recover-lifecycle-subscription-from-nuvla
  (mock/reset-state!)
  (swap! (var-get #'mock/mepm-state)
         assoc
         :mepm-id "mepm/test-1")
  (with-redefs [nuvla-backed-mepm/retrieve-nuvla-resource! (fn [resource-id]
                                                             (is (= "mepm/test-1" resource-id))
                                                             {:id "mepm/test-1"
                                                              :mm3-subscription-id "sub-from-nuvla"
                                                              :mm3-subscription-callback-uri "http://localhost:8200/api/mec/internal/mm3/app_lcm/v1/notifications"})]
    (testing "subscription metadata can be rehydrated from persisted MEPM resource"
      (is (= {:id                "sub-from-nuvla"
              :subscriptionId    "sub-from-nuvla"
              :callbackUri       "http://localhost:8200/api/mec/internal/mm3/app_lcm/v1/notifications"
              :notificationTypes ["AppInstNotification"
                                  "AppLcmOpOccNotification"]
              :source            :nuvla-recovered}
             (select-keys (mock/recover-lifecycle-subscription-from-nuvla!)
                          [:id :subscriptionId :callbackUri :notificationTypes :source])))
      (is (= "http://localhost:8200/api/mec/internal/mm3/app_lcm/v1/notifications"
             (get-in (mock/get-state) [:subscriptions "sub-from-nuvla" :callbackUri]))))))


(deftest test-recover-app-instances-from-nuvla
  (mock/reset-state!)
  (swap! (var-get #'mock/mepm-state)
         assoc
         :mepm-id "mepm/test-1")
  (with-redefs [nuvla-backed-mepm/query-nuvla-resources! (fn [resource-name filter-str & [_options]]
                                                           (is (= "deployment" resource-name))
                                                           (is (= "mec-mepm-id='mepm/test-1'" filter-str))
                                                           [{:id "deployment/mec-1"
                                                             :name "Recovered MEC deployment"
                                                             :module {:href "module/test-app"}
                                                             :created "2026-05-29T00:00:00.000Z"
                                                             :state "STARTED"
                                                             :mec-app-instance-id "deployment/mec-1"
                                                             :mec-backing-deployment-id "deployment/backing-1"
                                                             :mec-host-id "nuvlabox/edge-1"}
                                                            {:id "deployment/backing-1"
                                                             :mec-app-instance-id "deployment/mec-1"}])]
    (testing "existing MEC-facing deployments are rehydrated as tracked app instances"
      (is (= ["deployment/backing-1"]
             (-> (mock/recover-app-instances-from-nuvla!) keys sort vec)))
      (is (= "STARTED"
             (get-in (mock/get-state) [:app-instances "deployment/backing-1" :operationalState])))
      (is (= "INSTANTIATED"
             (get-in (mock/get-state) [:app-instances "deployment/backing-1" :instantiationState]))))))


(deftest test-backed-terminate-delete-emits-completed-operation
  (mock/reset-state!)
  (let [requests (atom [])]
    (swap! (var-get #'mock/mepm-state)
           assoc
           :backend-mode "NUVLA_BACKED"
           :mepm-id "mepm/mock-backed"
           :subscriptions {"sub-1" {:id "sub-1"
                                    :callbackUri "http://callback.local/mm3"
                                    :active true}}
           :app-instances {"deployment/backing-1" {:id "deployment/backing-1"
                                                   :instantiationState "INSTANTIATED"
                                                   :operationalState "STOPPED"}})
    (with-redefs [nuvla-backed-mepm/terminate-backing-deployment! (fn [_ payload]
                                                                    (is (= "deployment/backing-1" (:appInstanceId payload)))
                                                                    {:backing-deployment-id "deployment/backing-1"
                                                                     :action "delete"})
                  http/post (fn [url request]
                              (swap! requests conj {:url url :request request})
                              {:status 202})]
      (testing "delete completion returns operationId and emits completed terminate notification"
        (let [response (#'mock/handle-delete-app-instance {} "deployment/backing-1")
              notification (emitted-notification (:request (first @requests)))
              op-id (get-in response [:body :operationId])]
          (is (= 202 (:status response)))
          (is (some? op-id))
          (is (= "COMPLETED" (get-in @(var-get #'mock/mepm-state) [:operations op-id :operationState])))
          (is (nil? (get-in @(var-get #'mock/mepm-state) [:app-instances "deployment/backing-1"])))
          (is (= "AppLcmOpOccNotification" (:notificationType notification)))
          (is (= op-id (:operationId notification)))
          (is (= "TERMINATE" (:operationType notification)))
          (is (= "COMPLETED" (:operationState notification)))
          (is (= "NOT_INSTANTIATED" (:instantiationState notification))))))))


(deftest test-reconcile-backed-app-instance-step-emits-stop-notification
  (mock/reset-state!)
  (let [requests (atom [])]
    (swap! (var-get #'mock/mepm-state)
           assoc
           :backend-mode "NUVLA_BACKED"
           :subscriptions {"sub-1" {:id "sub-1"
                                    :callbackUri "http://callback.local/mm3"
                                    :active true}}
           :app-instances {"deployment/backing-1" {:id "deployment/backing-1"
                                                   :instantiationState "INSTANTIATED"
                                                   :operationalState "STARTED"}})
    (with-redefs [nuvla-backed-mepm/retrieve-backing-deployment! (fn [_]
                                                                   {:id "deployment/backing-1"
                                                                    :state "STOPPED"})
                  http/post (fn [url request]
                              (swap! requests conj {:url url :request request})
                              {:status 202})]
      (testing "out-of-band backing stop emits AppInstNotification and updates local state"
        (is (= :updated (#'mock/reconcile-backed-app-instance-step!
                          "deployment/backing-1"
                          (get-in (mock/get-state) [:app-instances "deployment/backing-1"]))))
        (is (= "STOPPED" (get-in (mock/get-state) [:app-instances "deployment/backing-1" :operationalState])))
        (is (= "AppInstNotification" (:notificationType (emitted-notification (:request (first @requests))))))
        (is (= "STOPPED" (:operationalState (emitted-notification (:request (first @requests))))))))))


(deftest test-reconcile-backed-app-instances-step-recovers-existing-instances
  (mock/reset-state!)
  (let [requests (atom [])]
    (swap! (var-get #'mock/mepm-state)
           assoc
           :backend-mode "NUVLA_BACKED"
           :mepm-id "mepm/test-1"
           :subscriptions {"sub-1" {:id "sub-1"
                                    :callbackUri "http://callback.local/mm3"
                                    :active true}})
    (with-redefs [nuvla-backed-mepm/query-nuvla-resources! (fn [_resource-name _filter-str & [_options]]
                                                             [{:id "deployment/mec-1"
                                                               :name "Recovered MEC deployment"
                                                               :module {:href "module/test-app"}
                                                               :created "2026-05-29T00:00:00.000Z"
                                                               :state "STARTED"
                                                               :mec-app-instance-id "deployment/mec-1"
                                                               :mec-backing-deployment-id "deployment/backing-1"
                                                               :mec-host-id "nuvlabox/edge-1"}])
                  nuvla-backed-mepm/retrieve-backing-deployment! (fn [_]
                                                                   {:id "deployment/backing-1"
                                                                    :state "STOPPED"})
                  http/post (fn [url request]
                              (swap! requests conj {:url url :request request})
                              {:status 202})]
      (testing "reconciler recovers older tracked instances and emits drift notification"
        (#'mock/reconcile-backed-app-instances-step!)
        (is (= "STOPPED" (get-in (mock/get-state) [:app-instances "deployment/backing-1" :operationalState])))
        (is (= "AppInstNotification" (:notificationType (emitted-notification (:request (first @requests))))))
        (is (= "STOPPED" (:operationalState (emitted-notification (:request (first @requests))))))))))
