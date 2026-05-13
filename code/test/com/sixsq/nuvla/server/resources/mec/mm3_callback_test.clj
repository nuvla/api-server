 (ns com.sixsq.nuvla.server.resources.mec.mm3-callback-test
   (:require
     [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.db.filter.parser :as parser]
     [com.sixsq.nuvla.server.resources.common.crud :as crud]
     [com.sixsq.nuvla.server.resources.mec :as mec]
     [com.sixsq.nuvla.server.resources.mec.mm3-callback :as mm3-callback]
     [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]))


 (def sample-mepm
   {:id "mepm/test-1"
    :mm3-subscription-id "mm3-sub-1"})


 (def sample-deployment
   {:id             "deployment/test-1"
    :state          "STOPPED"
    :module         {:href "module/test-app"
                     :content {:name "Test App"}}
    :module/content {:name "Test App"}})


 (def sample-instantiate-job
   {:id                         "job/instantiate-123"
    :action                     "start_deployment"
    :state                      "QUEUED"
    :mec-operation-type         "INSTANTIATE"
    :mec-southbound-operation-id "op-123"
    :target-resource            {:href "deployment/test-1"}
    :mepm-id                    "mepm/test-1"
    :start-time                 "2026-05-08T20:00:00Z"
    :state-entered-time         "2026-05-08T20:00:05Z"})


 (def sample-terminate-job
   {:id                         "job/terminate-123"
    :action                     "stop_deployment"
    :state                      "RUNNING"
    :mec-operation-type         "TERMINATE"
    :mec-southbound-operation-id "op-terminate-1"
    :target-resource            {:href "deployment/test-1"}
    :mepm-id                    "mepm/test-1"
    :start-time                 "2026-05-08T20:00:00Z"
    :state-entered-time         "2026-05-08T20:00:05Z"})


(def sample-operate-job
  {:id                         "job/operate-123"
   :action                     "stop_deployment"
   :state                      "RUNNING"
   :mec-operation-type         "OPERATE"
   :mec-southbound-operation-id "op-operate-1"
   :mec-request-params         {:changeStateTo "STOPPED"}
   :target-resource            {:href "deployment/test-1"}
   :mepm-id                    "mepm/test-1"
   :start-time                 "2026-05-08T20:00:00Z"
   :state-entered-time         "2026-05-08T20:00:05Z"})


 (deftest test-handle-processing-operation-notification
   (let [job-state  (atom sample-instantiate-job)
         edit-calls (atom [])
         op-calls   (atom [])]
     (with-redefs [crud/query-as-admin (fn [collection _options]
                                         (case collection
                                           "mepm" [1 [sample-mepm]]
                                           "job" [1 [@job-state]]
                                           [0 []]))
                   crud/retrieve-by-id-as-admin (fn [resource-id]
                                                  (case resource-id
                                                    "job/instantiate-123" @job-state
                                                    "deployment/test-1" sample-deployment
                                                    "mepm/test-1" sample-mepm
                                                    nil))
                   crud/edit-by-id-as-admin (fn [resource-id body]
                                              (swap! edit-calls conj [resource-id body])
                                              (when (= resource-id (:id @job-state))
                                                (swap! job-state merge body))
                                              {:status 200
                                               :body   (merge @job-state body)})
                   dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [op-occ change-type previous-state]
                                                                      (swap! op-calls conj [op-occ change-type previous-state])
                                                                      [])]
      (let [response (mm3-callback/handle-notification {:body {:notificationType "AppLcmOpOccNotification"
                                                                :subscriptionId   "mm3-sub-1"
                                                                :operationId      "op-123"
                                                                :operationState   "PROCESSING"}})]
         (is (= 202 (:status response)))
         (is (= "mepm/test-1" (ffirst @edit-calls)))
         (is (= "deployment/test-1" (first (second @edit-calls))))
         (is (= "job/instantiate-123" (first (nth @edit-calls 2))))
         (is (= "RUNNING" (get-in @edit-calls [2 1 :state])))
         (is (some? (get-in @edit-calls [2 1 :mec-last-notification :payload :operationId])))
         (is (= 1 (count @op-calls)))
         (is (= "PROCESSING" (get-in @op-calls [0 0 :operationState])))
         (is (= "OPERATION_STATE" (get-in @op-calls [0 1])))
         (is (= "STARTING" (get-in @op-calls [0 2])))))))


 (deftest test-handle-completed-terminate-notification
   (let [job-state        (atom sample-terminate-job)
         deployment-state (atom (assoc sample-deployment :state "STOPPING"))
         edit-calls       (atom [])]
     (with-redefs [crud/query-as-admin (fn [collection _options]
                                         (case collection
                                           "mepm" [1 [sample-mepm]]
                                           "job" [1 [@job-state]]
                                           [0 []]))
                   crud/retrieve-by-id-as-admin (fn [resource-id]
                                                  (case resource-id
                                                    "job/terminate-123" @job-state
                                                    "deployment/test-1" @deployment-state
                                                    "mepm/test-1" sample-mepm
                                                    nil))
                   crud/edit-by-id-as-admin (fn [resource-id body]
                                              (swap! edit-calls conj [resource-id body])
                                              (case resource-id
                                                "job/terminate-123" (swap! job-state merge body)
                                                "deployment/test-1" (swap! deployment-state merge body)
                                                nil)
                                              {:status 200
                                               :body   (case resource-id
                                                         "job/terminate-123" @job-state
                                                         "deployment/test-1" @deployment-state
                                                         nil)})]
      (let [response (mm3-callback/handle-notification {:body {:notificationType "AppLcmOpOccNotification"
                                                                :subscriptionId   "mm3-sub-1"
                                                                :operationId      "op-terminate-1"
                                                                :operationState   "COMPLETED"}})]
         (is (= 202 (:status response)))
         (is (= "mepm/test-1" (ffirst @edit-calls)))
         (is (= "deployment/test-1" (first (second @edit-calls))))
         (is (= "CREATED" (get-in @edit-calls [1 1 :state])))
         (is (= "job/terminate-123" (first (nth @edit-calls 2))))
         (is (= "SUCCESS" (get-in @edit-calls [2 1 :state])))
         (is (some? (get-in @edit-calls [2 1 :mec-last-notification :received])))))))


 (deftest test-handle-app-instance-notification
   (let [deployment-state (atom sample-deployment)
         app-calls        (atom [])
         edit-calls       (atom [])]
     (with-redefs [crud/query-as-admin (fn [collection _options]
                                         (case collection
                                           "mepm" [1 [sample-mepm]]
                                           "deployment-parameter" [1 [{:parent "deployment/test-1"}]]
                                           [0 []]))
                   crud/retrieve-by-id-as-admin (fn [resource-id]
                                                  (case resource-id
                                                    "deployment/test-1" @deployment-state
                                                    "mepm/test-1" sample-mepm
                                                    nil))
                   crud/edit-by-id-as-admin (fn [resource-id body]
                                              (swap! edit-calls conj [resource-id body])
                                              (when (= resource-id "deployment/test-1")
                                                (swap! deployment-state merge body))
                                              {:status 200
                                               :body   @deployment-state})
                   dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                    (swap! app-calls conj [app-instance change-type previous-state])
                                                                    [])]
      (let [response (mm3-callback/handle-notification {:body {:notificationType   "AppInstNotification"
                                                                :subscriptionId     "mm3-sub-1"
                                                                :appInstanceId      "southbound-app-1"
                                                                :instantiationState "INSTANTIATED"
                                                                :operationalState   "STARTED"}})]
         (is (= 202 (:status response)))
         (is (= "mepm/test-1" (ffirst @edit-calls)))
         (is (= "deployment/test-1" (first (second @edit-calls))))
         (is (= "STARTED" (get-in @edit-calls [1 1 :state])))
         (is (some? (get-in @edit-calls [1 1 :mec-last-notification :payload :appInstanceId])))
         (is (= 1 (count @app-calls)))
         (is (= "STARTED" (get-in @app-calls [0 0 :operationalState])))
         (is (= "OPERATIONAL_STATE" (get-in @app-calls [0 1])))
         (is (= "STOPPED" (get-in @app-calls [0 2])))))))


(deftest test-repeated-completed-notification-reconciles-stale-deployment
  (let [job-state        (atom (assoc sample-terminate-job :state "SUCCESS"))
        deployment-state (atom (assoc sample-deployment :state "STOPPED"))
        edit-calls       (atom [])
        app-calls        (atom [])]
    (with-redefs [crud/query-as-admin (fn [collection _options]
                                        (case collection
                                          "mepm" [1 [sample-mepm]]
                                          "job" [1 [@job-state]]
                                          [0 []]))
                  crud/retrieve-by-id-as-admin (fn [resource-id]
                                                 (case resource-id
                                                   "job/terminate-123" @job-state
                                                   "deployment/test-1" @deployment-state
                                                   "mepm/test-1" sample-mepm
                                                   nil))
                  crud/edit-by-id-as-admin (fn [resource-id body]
                                             (swap! edit-calls conj [resource-id body])
                                             (when (= resource-id "deployment/test-1")
                                               (swap! deployment-state merge body))
                                             {:status 200 :body @deployment-state})
                  dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                   (swap! app-calls conj [app-instance change-type previous-state])
                                                                   [])]
      (let [response (mm3-callback/handle-notification {:body {:notificationType   "AppLcmOpOccNotification"
                                                               :subscriptionId     "mm3-sub-1"
                                                               :operationId        "op-terminate-1"
                                                               :operationState     "COMPLETED"
                                                               :instantiationState "NOT_INSTANTIATED"}})]
        (is (= 202 (:status response)))
        (is (= "mepm/test-1" (ffirst @edit-calls)))
        (is (= "deployment/test-1" (first (second @edit-calls))))
        (is (= "CREATED" (get-in @edit-calls [1 1 :state])))
        (is (some? (get-in @edit-calls [1 1 :mec-last-notification :payload :instantiationState])))
        (is (= 1 (count @app-calls)))
        (is (= "NOT_INSTANTIATED" (get-in @app-calls [0 0 :instantiationState])))
        (is (= "INSTANTIATION_STATE" (get-in @app-calls [0 1])))
        (is (= "INSTANTIATED" (get-in @app-calls [0 2])))))))


(deftest test-failed-operation-uses-explicit-app-state
  (let [job-state        (atom sample-operate-job)
        deployment-state (atom (assoc sample-deployment :state "STARTED"))
        edit-calls       (atom [])
        app-calls        (atom [])]
    (with-redefs [crud/query-as-admin (fn [collection _options]
                                        (case collection
                                          "mepm" [1 [sample-mepm]]
                                          "job" [1 [@job-state]]
                                          [0 []]))
                  crud/retrieve-by-id-as-admin (fn [resource-id]
                                                 (case resource-id
                                                   "job/operate-123" @job-state
                                                   "deployment/test-1" @deployment-state
                                                   "mepm/test-1" sample-mepm
                                                   nil))
                  crud/edit-by-id-as-admin (fn [resource-id body]
                                             (swap! edit-calls conj [resource-id body])
                                             (case resource-id
                                               "job/operate-123" (swap! job-state merge body)
                                               "deployment/test-1" (swap! deployment-state merge body)
                                               nil)
                                             {:status 200
                                              :body   (case resource-id
                                                        "job/operate-123" @job-state
                                                        "deployment/test-1" @deployment-state
                                                        nil)})
                  dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                   (swap! app-calls conj [app-instance change-type previous-state])
                                                                   [])]
      (let [response (mm3-callback/handle-notification {:body {:notificationType   "AppLcmOpOccNotification"
                                                               :subscriptionId     "mm3-sub-1"
                                                               :operationId        "op-operate-1"
                                                               :operationState     "FAILED"
                                                               :instantiationState "INSTANTIATED"
                                                               :operationalState   "STOPPED"
                                                               :error              {:detail "MEPM rejected stop"}}})]
        (is (= 202 (:status response)))
        (is (= "mepm/test-1" (ffirst @edit-calls)))
        (is (= "deployment/test-1" (first (second @edit-calls))))
        (is (= "STOPPED" (get-in @edit-calls [1 1 :state])))
        (is (= "job/operate-123" (first (nth @edit-calls 2))))
        (is (= "FAILED" (get-in @edit-calls [2 1 :state])))
        (is (= "MEPM rejected stop" (get-in @edit-calls [2 1 :status-message])))
        (is (some? (get-in @edit-calls [2 1 :mec-last-notification :error :detail])))
        (is (= 1 (count @app-calls)))
        (is (= "STOPPED" (get-in @app-calls [0 0 :operationalState])))
        (is (= "OPERATIONAL_STATE" (get-in @app-calls [0 1])))
        (is (= "STARTED" (get-in @app-calls [0 2])))))))


 (deftest test-top-level-route-mounts-mm3-callback
   (testing "top-level MEC handler routes internal Mm3 callback requests"
     (with-redefs [mm3-callback/handle-notification (fn [_] {:status 202 :body {:ok true}})]
       (let [response (mec/routes {:request-method :post
                                   :uri "/api/mec/internal/mm3/app_lcm/v1/notifications"
                                   :params {}
                                   :body {}})]
         (is (= 202 (:status response)))
         (is (= {:ok true} (:body response)))))))

(deftest test-handle-notification-uses-cimi-filtered-correlation-queries
  (let [job-state        (atom sample-instantiate-job)
        deployment-state (atom sample-deployment)
        query-calls      (atom [])]
    (with-redefs [crud/query-as-admin (fn [collection options]
                                        (swap! query-calls conj [collection options])
                                        (let [filter-expr (get-in options [:cimi-params :filter])]
                                          (case collection
                                            "mepm" (if (= (parser/parse-cimi-filter "mm3-subscription-id='mm3-sub-1'") filter-expr)
                                                     [1 [sample-mepm]]
                                                     [0 []])
                                            "job" (if (= (parser/parse-cimi-filter "mec-southbound-operation-id='op-123'") filter-expr)
                                                    [1 [@job-state]]
                                                    [0 []])
                                            [0 []])))
                  crud/retrieve-by-id-as-admin (fn [resource-id]
                                                 (case resource-id
                                                   "deployment/test-1" @deployment-state
                                                   "mepm/test-1" sample-mepm
                                                   nil))
                  crud/edit-by-id-as-admin (fn [resource-id body]
                                             (case resource-id
                                               "job/instantiate-123" (swap! job-state merge body)
                                               "deployment/test-1" (swap! deployment-state merge body)
                                               nil)
                                             {:status 200 :body body})
                  dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [& _] [])
                  dispatcher/dispatch-app-instance-state-change! (fn [& _] [])]
      (let [response (mm3-callback/handle-notification {:body {:notificationType   "AppLcmOpOccNotification"
                                                               :subscriptionId     "mm3-sub-1"
                                                               :operationId        "op-123"
                                                               :operationState     "COMPLETED"
                                                               :appInstanceId      "southbound-app-1"
                                                               :instantiationState "INSTANTIATED"
                                                               :operationalState   "STARTED"}})]
        (is (= 202 (:status response)))
        (is (= [["mepm" {:cimi-params {:last 2
                                       :filter (parser/parse-cimi-filter "mm3-subscription-id='mm3-sub-1'")}}]
                ["job" {:cimi-params {:last 2
                                      :filter (parser/parse-cimi-filter "mec-southbound-operation-id='op-123'")}}]]
               @query-calls))))))
