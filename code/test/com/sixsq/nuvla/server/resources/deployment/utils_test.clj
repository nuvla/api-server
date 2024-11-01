(ns com.sixsq.nuvla.server.resources.deployment.utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is testing]]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.pricing.payment :as payment]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.deployment.utils :as t])
  (:import (clojure.lang ExceptionInfo)))


(deftest throw-when-payment-required
  (testing "stripe is disabled"
    (with-redefs [config-nuvla/*stripe-api-key* nil
                  a/is-admin?                   (constantly false)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "is admin"
    (with-redefs [config-nuvla/*stripe-api-key* "123"
                  a/is-admin?                   (constantly true)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer active and can pay"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly true)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer is trialing"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly false)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer past_due and has not payment"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "past_due"})
                  payment/can-pay?                   (constantly false)]
      (is (= {} (t/throw-when-payment-required {} {})))))
  (testing "customer active and price set but can't pay"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly false)]
      (is (= {} (t/throw-when-payment-required {} {})))))
  (let [billable-deployment-follow     {:module {:price {:price-id              "price_id"
                                                         :follow-customer-trial true}}}
        billable-deployment-not-follow {:module {:price {:price-id              "price_id"
                                                         :follow-customer-trial false}}}]
    (testing "customer is active"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->subscription (constantly {:status "active"})]
        (is (= billable-deployment-follow
               (t/throw-when-payment-required billable-deployment-follow {})))))
    (testing "customer is past_due"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->subscription (constantly {:status "past_due"})]
        (is (= billable-deployment-follow
               (t/throw-when-payment-required billable-deployment-follow {})))))
    (testing "customer trialing with module price set and follow trial"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly false)]
        (is (= (t/throw-when-payment-required billable-deployment-follow {})
               billable-deployment-follow))))
    (testing "customer trialing with module price set and follow trial is not set"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly false)]
        (is (thrown-with-msg? ExceptionInfo
                              #"Valid subscription and payment method are needed!"
                              (t/throw-when-payment-required billable-deployment-not-follow {})))))

    (testing "customer trialing with module price set and follow trial is not set but can pay"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly true)]
        (is (= (t/throw-when-payment-required billable-deployment-not-follow {})
               billable-deployment-not-follow))))
    (testing "vendor or user with edit-data rights"
      (with-redefs [config-nuvla/*stripe-api-key* "123"
                    a/can-edit-data?              (constantly true)]
        (is (= (t/throw-when-payment-required billable-deployment-not-follow {})
               billable-deployment-not-follow))))))

(deftest cred-edited?
  (are [expected parent current-parent]
    (= expected (t/cred-edited? parent current-parent))
    false nil nil
    false nil "credential/x"
    false "credential/x" "credential/x"
    true "credential/y" "credential/x"))

(deftest keep-defined-values
  (let [m-a {:name "a" :value "a-v" :description "a-d"}
        m-b {:name "b" :description "b-d"}]
    (are [expected arg-map]
      (= expected (t/keep-current-defined-values (:arg1 arg-map) (:arg2 arg-map)))
      [] {:arg1 [] :arg2 []}
      [] {:arg1 [m-a] :arg2 []}
      [m-a] {:arg1 [m-a] :arg2 [{:name "a" :description "a-d"}]}
      [m-a m-b] {:arg1 [m-a m-b] :arg2 [{:name "a" :description "a-d"} m-b]}
      [(assoc m-a :description "a-d-changed") m-b] {:arg1 [m-a m-b] :arg2 [{:name "a" :description "a-d-changed"} m-b]}
      [m-a m-b] {:arg1 [m-a m-b] :arg2 [m-a (assoc m-b :value "any")]})))

(deftest throw-can-not-access-helm-repo-cred
  (is (= (t/throw-can-not-access-helm-repo-cred {} {}) {})))

(deftest get-deployment-state
  (is (= #{{:parent  "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name    "agent.image",
            :value   "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
            :node-id "agent",
            :id      "deployment-parameter/9d05074c-c797-3db4-aac3-c306d23face1"}
           {:parent  "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name    "agent.node-id",
            :value   "agent",
            :node-id "agent",
            :id      "deployment-parameter/7379096e-a144-316a-9b13-ca433c32d0f0"}
           {:parent  "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name    "agent.service-id",
            :value   "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef",
            :node-id "agent",
            :id      "deployment-parameter/ad63df24-6ba8-3cca-a613-3ae283da2a15"}
           {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name   "hostname",
            :value  "178.194.193.87",
            :id     "deployment-parameter/92871bb0-52ef-3509-9f8c-fe4d95a40157"}
           {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name   "ip.local",
            :value  "",
            :id     "deployment-parameter/16f7d4ac-0b34-37e8-8cf3-dff5449e2712"}
           {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name   "ip.public",
            :value  "178.194.193.87",
            :id     "deployment-parameter/a26e25ca-1367-32aa-aa03-83083c0e0887"}
           {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name   "ip.swarm",
            :value  "192.168.64.4",
            :id     "deployment-parameter/bde74959-94aa-3599-b146-bac88c48d244"}
           {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
            :name   "ip.vpn",
            :value  "",
            :id     "deployment-parameter/666dd1d0-cb68-32c0-97f5-51cd5c455b41"}}
         (set (t/get-deployment-state
                {:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                 :module {:compatibility "docker-compose"
                          :subtype       "application"}}
                {:id            "nuvlabox-status/fb4da83b-e911-4f01-8bed-82a8473ac8e3",
                 :ip            "178.194.193.87",
                 :network       {:ips
                                 {:public "178.194.193.87",
                                  :swarm  "192.168.64.4",
                                  :vpn    "",
                                  :local  ""}}
                 :coe-resources {:docker
                                 {:containers [{:Id "f271e2566489ab4beca2ac193935076f8cf89b5ab768c78962a4ea028c9ab51c"},
                                               {:Image  "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
                                                :Labels {:com.docker.compose.service "agent",
                                                         :com.docker.compose.project "b3b70820-2de4-4a11-b00c-a79661c3d433"},
                                                :Id     "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}]}}}))))
  (is (= #{{:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "node_exporter.image",
            :value   "quay.io/prometheus/node-exporter:latest",
            :node-id "node_exporter",
            :id      "deployment-parameter/a85cebf7-17b0-324e-a2ec-a1143be6056d"}
           {:parent "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name   "hostname",
            :value  "10.0.133.172",
            :id     "deployment-parameter/ea930503-cd39-369f-bc3f-e455f1ddf024"}
           {:parent "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name   "ip.local",
            :value  "10.160.3.157",
            :id     "deployment-parameter/00e08780-9056-3bd6-a38f-88d3662d881f"}
           {:parent "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name   "ip.public",
            :value  "143.233.127.6",
            :id     "deployment-parameter/471cdd6f-0e90-3a76-baf8-77831eff75d9"}
           {:parent "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name   "ip.swarm",
            :value  "10.160.3.194",
            :id     "deployment-parameter/be6612c6-628e-3eab-9fba-81cef3ae7648"}
           {:parent "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name   "ip.vpn",
            :value  "10.0.133.172",
            :id     "deployment-parameter/1fefb4e4-b7bb-346b-92b9-acd3a3c00d1e"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "node_exporter.mode",
            :value   "replicated",
            :node-id "node_exporter",
            :id      "deployment-parameter/11f8e5d0-6ca1-370e-9bf9-e7be1354ca61"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "node_exporter.node-id",
            :value   "node_exporter",
            :node-id "node_exporter",
            :id      "deployment-parameter/bd11f3f7-f962-3556-97f5-3335cdb8c58e"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "node_exporter.replicas.desired",
            :value   "1",
            :node-id "node_exporter",
            :id      "deployment-parameter/679d66d0-631c-3a3d-9d6b-c901c47a0d81",}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "node_exporter.replicas.running",
            :value   "1",
            :node-id "node_exporter",
            :id      "deployment-parameter/6f858e11-1aee-3a95-85ab-5e83af8bb828"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "node_exporter.service-id",
            :value   "8zjrszesw70t",
            :node-id "node_exporter",
            :id      "deployment-parameter/2ea2cf7d-75f3-3a66-ae58-fd71bbe8bebd"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "otelcol.image",
            :value   "harbor.res.eng.it/icos/meta-kernel/observability/otel-collector-icos-distribution:1.2.0-v0.92.0-1",
            :node-id "otelcol",
            :id      "deployment-parameter/66ef5e70-34ca-34c9-838c-a1b05ddd8b34"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "otelcol.mode",
            :value   "replicated",
            :node-id "otelcol",
            :id      "deployment-parameter/49553853-8843-35a4-b6a0-f29e33fa5d2b"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "otelcol.node-id",
            :value   "otelcol",
            :node-id "otelcol",
            :id      "deployment-parameter/77b44cf0-ba8c-380f-847d-2d12fefd88c6"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "otelcol.replicas.desired",
            :value   "1",
            :node-id "otelcol",
            :id      "deployment-parameter/758b85b3-7212-3c75-8833-7861a53b4ead"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "otelcol.replicas.running",
            :value   "1",
            :node-id "otelcol",
            :id      "deployment-parameter/41d1dc8f-e40c-379b-8f90-97a5449bb599"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "otelcol.service-id",
            :value   "uuip6ca6fwsn",
            :node-id "otelcol",
            :id      "deployment-parameter/956fd55c-a1ec-3a34-9e7d-915a2dfa0884"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "telemetruum_leaf_exporter.image",
            :value   "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0",
            :node-id "telemetruum_leaf_exporter",
            :id      "deployment-parameter/8e9ad322-5016-365c-aa40-ad75fc485a75"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "telemetruum_leaf_exporter.mode",
            :value   "replicated",
            :node-id "telemetruum_leaf_exporter",
            :id      "deployment-parameter/8388dd76-0097-3dc3-a5c1-04eb53dc6f26"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "telemetruum_leaf_exporter.node-id",
            :value   "telemetruum_leaf_exporter",
            :node-id "telemetruum_leaf_exporter",
            :id      "deployment-parameter/9e969e18-442d-36d7-9907-dc8680d373b5"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "telemetruum_leaf_exporter.replicas.desired",
            :value   "1",
            :node-id "telemetruum_leaf_exporter",
            :id      "deployment-parameter/9b89bb0b-98cb-35cf-9b1b-9e73ef8ecbe6"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "telemetruum_leaf_exporter.replicas.running",
            :value   "1",
            :node-id "telemetruum_leaf_exporter",
            :id      "deployment-parameter/45c8a074-2a03-37ce-9cf8-6d8a88943940"}
           {:parent  "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39",
            :name    "telemetruum_leaf_exporter.service-id",
            :value   "t5smdvbjrht7",
            :node-id "telemetruum_leaf_exporter",
            :id      "deployment-parameter/f4286a03-43f5-3dc8-8878-0231398cc635"}}
         (set (t/get-deployment-state
                {:id     "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                 :module {:compatibility "swarm"
                          :subtype       "application"}}
                {:ip            "10.0.133.172",
                 :coe-resources {:docker
                                 {:services
                                  [{:ID            "8zjrszesw70t88thnna2eehxw",
                                    :Spec          {:Name   "395a87fa-6b53-4e76-8a36-eccf8a19bc39_node_exporter",
                                                    :Labels {:com.docker.stack.image     "quay.io/prometheus/node-exporter:latest",
                                                             :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                                    :Mode   {:Replicated {:Replicas 1}}},
                                    :ServiceStatus {:RunningTasks 1,
                                                    :DesiredTasks 1}}
                                   {:ID            "uuip6ca6fwsnj0fqeoz68o15a",
                                    :Spec          {:Name         "395a87fa-6b53-4e76-8a36-eccf8a19bc39_otelcol",
                                                    :Labels       {:com.docker.stack.image     "harbor.res.eng.it/icos/meta-kernel/observability/otel-collector-icos-distribution:1.2.0-v0.92.0-1",
                                                                   :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                                    :Mode         {:Replicated {:Replicas 1}}},
                                    :ServiceStatus {:RunningTasks 1,
                                                    :DesiredTasks 1}}
                                   {:ID            "t5smdvbjrht7sd0cfki9nu4qj",
                                    :Spec          {:Name         "395a87fa-6b53-4e76-8a36-eccf8a19bc39_telemetruum_leaf_exporter",
                                                    :Labels       {:com.docker.stack.image     "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0",
                                                                   :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                                    :Mode         {:Replicated {:Replicas 1}}},
                                    :ServiceStatus {:RunningTasks 1,
                                                    :DesiredTasks 1}}]}},
                 :network       {:ips {:public "143.233.127.6",
                                       :swarm  "10.160.3.194",
                                       :vpn    "10.0.133.172",
                                       :local  "10.160.3.157"}}})))))


;; to build a list of deployments states to be updated
;; do not start from coe-resources but from deployment
;; because compose apps has only a uuid registered as label "com.docker.compose.project".
;; so for each deployment depending on type dig into coe resources and status to get values to update them afterward
;; it has also the advantage to remove the need to check if deployment is really running on this NE

;; query deployments in state started
;; for each of deployment running on the NE depending on type check docker containers/services or kubernetes resources
;; build a bulk update query on parameters
