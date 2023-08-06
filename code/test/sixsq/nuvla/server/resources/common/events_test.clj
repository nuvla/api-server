(ns sixsq.nuvla.server.resources.common.events-test
  (:require [clojure.test :refer [deftest testing is]]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.server.resources.common.events :as events]
            [sixsq.nuvla.server.resources.event :as event]
            [sixsq.nuvla.server.util.time :as time]))


(deftest events-disabled-for-event-resource
  (is (false? (events/events-enabled event/resource-type))))


#_(deftest default-events-config
  (let [{:keys [enabled default-severity]}
        (eventing/events-configuration "fake-resource" :fake-op nil)]
    (testing "Events enabled by default"
      (is (true? enabled)))
    (testing "Events default severity should be `medium`"
      (is (= "medium" default-severity))))
  (testing "Default config for :crud/add"
    (let [crud-add-default-201-config (eventing/events-configuration "fake-resource"
                                                                     :crud/add
                                                                     {:response {:status 201}})]
      (is (= {:enabled            true
              :default-event-type "resource.created"
              :default-category   "crud"
              :default-severity   "medium"
              :resource-href-prov :response-resource-id
              :message-prov       :response-message
              :log-request-body   true
              :log-response-body  true}
             (select-keys crud-add-default-201-config
                          [:enabled :default-event-type :default-category :default-severity
                           :resource-href-prov :message-prov :log-request-body :log-response-body]))))
    (let [crud-add-default-400-config (eventing/events-configuration "fake-resource"
                                                                     :crud/add
                                                                     {:response {:status 400}})]
      (is (false? (:enabled crud-add-default-400-config)))))

  (testing "Default config for :crud/edit"
    (let [crud-edit-default-200-config (eventing/events-configuration "fake-resource"
                                                                      :crud/edit
                                                                      {:response {:status 200}})]
      (is (= {:enabled            true
              :default-event-type "resource.updated"
              :default-category   "crud"
              :default-severity   "medium"
              :resource-href-prov :response-id
              :message-prov       :response-message
              :log-request-body   true
              :log-response-body  true}
             (select-keys crud-edit-default-200-config
                          [:enabled :default-event-type :default-category :default-severity
                           :resource-href-prov :message-prov :log-request-body :log-response-body]))))
    (let [crud-edit-default-400-config (eventing/events-configuration "fake-resource"
                                                                      :crud/edit
                                                                      {:response {:status 400}})]
      (is (false? (:enabled crud-edit-default-400-config)))))

  (testing "Default config for :crud/delete"
    (let [crud-delete-default-200-config (eventing/events-configuration "fake-resource"
                                                                        :crud/delete
                                                                        {:response {:status 200}})]
      (is (= {:enabled            true
              :default-event-type "resource.deleted"
              :default-category   "crud"
              :default-severity   "medium"
              :resource-href-prov :response-resource-id}
             (select-keys crud-delete-default-200-config
                          [:enabled :default-event-type :default-category :default-severity
                           :resource-href-prov :message-prov :log-request-body :log-response-body]))))
    (let [crud-delete-default-400-config (eventing/events-configuration "fake-resource"
                                                                        :crud/delete
                                                                        {:response {:status 400}})]
      (is (false? (:enabled crud-delete-default-400-config))))))


