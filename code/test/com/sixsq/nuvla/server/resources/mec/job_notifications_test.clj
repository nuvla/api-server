(ns com.sixsq.nuvla.server.resources.mec.job-notifications-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.deployment]
    [com.sixsq.nuvla.server.resources.deployment.utils :as deployment-utils]
    [com.sixsq.nuvla.server.resources.job.interface :as job-interface]
    [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]))


(def sample-started-deployment
  {:id             "deployment/test-123"
   :state          "STARTED"
   :module         {:href "module/test-app"
                    :content {:name "Test App"}}
   :module/content {:name "Test App"}})


(def sample-stopped-deployment
  {:id             "deployment/test-123"
   :state          "STOPPED"
   :module         {:href "module/test-app"
                    :content {:name "Test App"}}
   :module/content {:name "Test App"}})


(deftest test-mec-job-on-done-dispatches-final-operation-and-app-instance
  (testing "successful instantiate completion dispatches operation result and app instance change"
    (let [op-calls  (atom [])
          app-calls (atom [])
          job       {:id                  "job/instantiate-123"
                     :action              "start_deployment"
                     :state               "SUCCESS"
                     :mec-operation-type  "INSTANTIATE"
                     :mec-app-instance-id "deployment/test-123"
                     :target-resource     {:href "deployment/test-123"}
                     :start-time          "2026-05-04T07:00:00Z"
                     :state-entered-time  "2026-05-04T07:01:00Z"}]
      (with-redefs [crud/retrieve-by-id-as-admin (fn [_] sample-started-deployment)
                    dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [op-occ change-type previous-state]
                                                                       (swap! op-calls conj [op-occ change-type previous-state])
                                                                       [])
                    dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                     (swap! app-calls conj [app-instance change-type previous-state])
                                                                     [])]
        (job-interface/on-done job))
      (is (= 1 (count @op-calls)))
      (is (= "COMPLETED" (get-in @op-calls [0 0 :operationState])))
      (is (= "OPERATION_RESULT" (get-in @op-calls [0 1])))
      (is (= "PROCESSING" (get-in @op-calls [0 2])))
      (is (= 1 (count @app-calls)))
      (is (= "deployment/test-123" (get-in @app-calls [0 0 :id])))
      (is (= "INSTANTIATED" (get-in @app-calls [0 0 :instantiation-state])))
      (is (= "STARTED" (get-in @app-calls [0 0 :operational-state])))
      (is (= "INSTANTIATION_STATE" (get-in @app-calls [0 1])))
      (is (= "NOT_INSTANTIATED" (get-in @app-calls [0 2]))))))


(deftest test-mec-job-on-done-failure-dispatches-operation-only
  (testing "failed operate completion dispatches final operation result only"
    (let [op-calls  (atom [])
          app-calls (atom [])
          job       {:id                  "job/operate-123"
                     :action              "start_deployment"
                     :state               "FAILED"
                     :mec-operation-type  "OPERATE"
                     :mec-app-instance-id "deployment/test-123"
                     :mec-request-params  {:changeStateTo "STARTED"}
                     :target-resource     {:href "deployment/test-123"}
                     :start-time          "2026-05-04T07:00:00Z"
                     :state-entered-time  "2026-05-04T07:01:00Z"
                     :status-message      "MEPM rejected request"}]
      (with-redefs [crud/retrieve-by-id-as-admin (fn [_] sample-started-deployment)
                    dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [op-occ change-type previous-state]
                                                                       (swap! op-calls conj [op-occ change-type previous-state])
                                                                       [])
                    dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                     (swap! app-calls conj [app-instance change-type previous-state])
                                                                     [])]
        (job-interface/on-done job))
      (is (= 1 (count @op-calls)))
      (is (= "FAILED" (get-in @op-calls [0 0 :operationState])))
      (is (= "OPERATION_RESULT" (get-in @op-calls [0 1])))
      (is (= "PROCESSING" (get-in @op-calls [0 2])))
      (is (empty? @app-calls)))))


(deftest test-mec-job-on-cancel-dispatches_failed_operation
  (testing "canceled terminate dispatches failed operation result without app instance change"
    (let [op-calls  (atom [])
          app-calls (atom [])
          job       {:id                  "job/terminate-123"
                     :action              "stop_deployment"
                     :state               "CANCELED"
                     :mec-operation-type  "TERMINATE"
                     :mec-app-instance-id "deployment/test-123"
                     :target-resource     {:href "deployment/test-123"}
                     :start-time          "2026-05-04T07:00:00Z"
                     :state-entered-time  "2026-05-04T07:01:00Z"}]
      (with-redefs [deployment-utils/on-cancel (fn [_] {:status 200})
                    dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [op-occ change-type previous-state]
                                                                       (swap! op-calls conj [op-occ change-type previous-state])
                                                                       [])
                    dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                     (swap! app-calls conj [app-instance change-type previous-state])
                                                                     [])]
        (job-interface/on-cancel job))
      (is (= 1 (count @op-calls)))
      (is (= "FAILED" (get-in @op-calls [0 0 :operationState])))
      (is (= "OPERATION_RESULT" (get-in @op-calls [0 1])))
      (is (= "PROCESSING" (get-in @op-calls [0 2])))
      (is (empty? @app-calls)))))


(deftest test-mec-job-on-timeout-dispatches_failed_operation
  (testing "timed out operate dispatches failed operation result without app instance change"
    (let [op-calls  (atom [])
          app-calls (atom [])
          job       {:id                  "job/operate-456"
                     :action              "stop_deployment"
                     :state               "CANCELED"
                     :mec-operation-type  "OPERATE"
                     :mec-app-instance-id "deployment/test-123"
                     :mec-request-params  {:changeStateTo "STOPPED"}
                     :target-resource     {:href "deployment/test-123"}
                     :start-time          "2026-05-04T07:00:00Z"
                     :state-entered-time  "2026-05-04T07:01:00Z"}]
      (with-redefs [deployment-utils/on-cancel (fn [_] {:status 200})
                    dispatcher/dispatch-app-lcm-op-occ-state-change! (fn [op-occ change-type previous-state]
                                                                       (swap! op-calls conj [op-occ change-type previous-state])
                                                                       [])
                    dispatcher/dispatch-app-instance-state-change! (fn [app-instance change-type previous-state]
                                                                     (swap! app-calls conj [app-instance change-type previous-state])
                                                                     [])]
        (job-interface/on-timeout job))
      (is (= 1 (count @op-calls)))
      (is (= "FAILED" (get-in @op-calls [0 0 :operationState])))
      (is (= "OPERATION_RESULT" (get-in @op-calls [0 1])))
      (is (= "PROCESSING" (get-in @op-calls [0 2])))
      (is (empty? @app-calls)))))
