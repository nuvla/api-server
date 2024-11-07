(ns com.sixsq.nuvla.server.resources.nuvlabox.status-utils-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.nuvlabox.data-utils :as data-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.status-utils :as t]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.resources.ts-nuvlaedge-availability :as ts-nuvlaedge-availability]
    [com.sixsq.nuvla.server.util.time :as time]))

(deftest status-fields-to-denormalize
  (are [expected nuvlabox-status]
    (= expected (t/status-fields-to-denormalize nuvlabox-status))
    {} nil
    {} {:other true}
    {:online true} {:online true}

    {:online false :inferred-location [46.2 6.1]}
    {:online false :inferred-location [46.2 6.1] :other false}

    {:inferred-location [46.2 6.1] :nuvlabox-engine-version "2.9.0"}
    {:inferred-location [46.2 6.1] :nuvlabox-engine-version "2.9.0" :other "x"}))

(deftest denormalize-changes-nuvlabox
  (testing "nuvlabox is not retrieved when no fields to be propagated"
    (let [called (atom false)]
      (with-redefs [crud/retrieve-by-id-as-admin #(reset! called true)]
        (t/denormalize-changes-nuvlabox {:other "x"})
        (is (false? @called)))))
  (testing "nuvlabox is edited when propagated field value is changed"
    (with-redefs [crud/retrieve-by-id-as-admin (constantly {:online true})
                  db/scripted-edit             (fn [_id & [{{:keys [doc]} :body}]] (is (false? (:online doc))))]
      (t/denormalize-changes-nuvlabox {:online false})))
  (testing "nuvlabox is edited when some propagated fields values are changed"
    (with-redefs [crud/retrieve-by-id-as-admin (constantly {:online                  true
                                                            :nuvlabox-engine-version "1.0.0"})
                  db/scripted-edit             (fn [_id & [{{:keys [doc]} :body}]] (is (= {:inferred-location       [46.2 6.1]
                                                                                           :nuvlabox-engine-version "2.0.0"
                                                                                           :online                  true}
                                                                                          doc)))]
      (t/denormalize-changes-nuvlabox {:online                  true
                                       :inferred-location       [46.2 6.1]
                                       :nuvlabox-engine-version "2.0.0"}))))

(deftest nuvlabox-status->ts-bulk-insert
  (let [nuvlaedge-id                      "nuvlabox/1"
        sampling-time                     "2023-05-10T08:13:17Z"
        nuvlabox-status                   {:id            "nuvlabox-status/f17b6239-44a9-41a1-b732-43ba11cc1af2",
                                           :resource-type "nuvlabox-status",
                                           :parent        nuvlaedge-id,
                                           :current-time  sampling-time,
                                           :updated       "2023-05-10T08:18:17.636Z",
                                           :created       "2022-01-10T15:56:39.017Z",
                                           :online        true
                                           :resources     {:cpu               {:load                0.45,
                                                                               :system-calls        0,
                                                                               :capacity            4,
                                                                               :interrupts          5667887,
                                                                               :topic               "cpu",
                                                                               :software-interrupts 4721525,
                                                                               :raw-sample          "{\"capacity\": 4, \"load\": 0.45, \"load-1\": 0.29, \"load-5\": 0.33, \"context-switches\": 18627865, \"interrupts\": 5667887, \"software-interrupts\": 4721525, \"system-calls\": 0}",
                                                                               :load-5              0.33,
                                                                               :context-switches    18627865,
                                                                               :load-1              0.29},
                                                           :ram               {:topic      "ram",
                                                                               :raw-sample "{\"capacity\": 32076, \"used\": 6445}",
                                                                               :capacity   32076,
                                                                               :used       6445},
                                                           :disks             [{:device     "sda1",
                                                                                :capacity   4,
                                                                                :used       2,
                                                                                :topic      "disks",
                                                                                :raw-sample "{\"device\": \"sda1\", \"capacity\": 4, \"used\": 2}"}
                                                                               {:device     "sda2",
                                                                                :capacity   2,
                                                                                :used       0,
                                                                                :topic      "disks",
                                                                                :raw-sample "{\"device\": \"sda2\", \"capacity\": 2, \"used\": 0}"}],
                                                           :net-stats         [{:interface         "enp0s31f6",
                                                                                :bytes-transmitted 49143,
                                                                                :bytes-received    260519}
                                                                               {:interface         "vpn",
                                                                                :bytes-transmitted 1051424260,
                                                                                :bytes-received    142997340}
                                                                               {:interface         "vethd2e8653",
                                                                                :bytes-transmitted 1862,
                                                                                :bytes-received    1848}
                                                                               {:interface         "br_LAN",
                                                                                :bytes-transmitted 55559,
                                                                                :bytes-received    244881}]
                                                           :power-consumption [{:metric-name "CPU_current", :energy-consumption 112, :unit "mA"}
                                                                               {:metric-name "CPU_voltage", :energy-consumption 19296, :unit "mV"}
                                                                               {:metric-name "CPU_power", :energy-consumption 2161, :unit "mW"}]}}
        expected-bulk-insert-request-body [{:nuvlaedge-id nuvlaedge-id
                                            :metric       "cpu"
                                            :timestamp    sampling-time
                                            :cpu          {:capacity            4,
                                                           :load                0.45,
                                                           :load-1              0.29,
                                                           :load-5              0.33,
                                                           :context-switches    18627865,
                                                           :interrupts          5667887,
                                                           :software-interrupts 4721525,
                                                           :system-calls        0}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "ram"
                                            :timestamp    sampling-time
                                            :ram          {:capacity 32076,
                                                           :used     6445}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "disk"
                                            :timestamp    sampling-time
                                            :disk         {:device   "sda1",
                                                           :capacity 4,
                                                           :used     2}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "disk"
                                            :timestamp    sampling-time
                                            :disk         {:device   "sda2",
                                                           :capacity 2,
                                                           :used     0}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "network"
                                            :timestamp    sampling-time
                                            :network      {:interface         "enp0s31f6",
                                                           :bytes-transmitted 49143,
                                                           :bytes-received    260519}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "network"
                                            :timestamp    sampling-time
                                            :network      {:interface         "vpn",
                                                           :bytes-transmitted 1051424260,
                                                           :bytes-received    142997340}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "network"
                                            :timestamp    sampling-time
                                            :network      {:interface         "vethd2e8653",
                                                           :bytes-transmitted 1862,
                                                           :bytes-received    1848}}
                                           {:nuvlaedge-id nuvlaedge-id
                                            :metric       "network"
                                            :timestamp    sampling-time
                                            :network      {:interface         "br_LAN",
                                                           :bytes-transmitted 55559,
                                                           :bytes-received    244881}}
                                           {:nuvlaedge-id      nuvlaedge-id
                                            :metric            "power-consumption"
                                            :timestamp         sampling-time
                                            :power-consumption {:metric-name "CPU_current", :energy-consumption 112, :unit "mA"}}
                                           {:nuvlaedge-id      nuvlaedge-id
                                            :metric            "power-consumption"
                                            :timestamp         sampling-time
                                            :power-consumption {:metric-name "CPU_voltage", :energy-consumption 19296, :unit "mV"}}
                                           {:nuvlaedge-id      nuvlaedge-id
                                            :metric            "power-consumption"
                                            :timestamp         sampling-time
                                            :power-consumption {:metric-name "CPU_power", :energy-consumption 2161, :unit "mW"}}]]
    (testing "nuvlabox status -> metric time-serie conversion for nuvlabox without hearthbeat support"
      (let [now (time/now)]
        (with-redefs [time/now (constantly now)]
          (is (= {:body        {:nuvlaedge-id "nuvlabox/1"
                                :online       1
                                :timestamp    (time/to-str now)}
                  :nuvla/authn auth/internal-identity
                  :params      {:resource-name ts-nuvlaedge-availability/resource-type}}
                 (with-redefs [crud/retrieve-by-id-as-admin (constantly {:refresh-interval 60})]
                   (data-utils/nuvlabox-status->insert-availability-request nuvlabox-status false))))
          (is (= {:body        {:nuvlaedge-id "nuvlabox/1"
                                :online       1
                                :timestamp    (time/to-str now)}
                  :nuvla/authn auth/internal-identity
                  :params      {:resource-name ts-nuvlaedge-availability/resource-type}}
                 (with-redefs [crud/retrieve-by-id-as-admin (constantly {:refresh-interval 60})
                               time/now-str                 (constantly "now")]
                   (data-utils/nuvlabox-status->insert-availability-request nuvlabox-status true)))))))
    (testing "nuvlabox status -> metric time-serie conversion for nuvlabox with heartbeat support"
      (let [now (time/now)]
        (with-redefs [time/now (constantly now)]
          (is (= {:body        {:nuvlaedge-id "nuvlabox/1"
                                :online       1
                                :timestamp    (time/to-str now)}
                  :nuvla/authn auth/internal-identity
                  :params      {:resource-name ts-nuvlaedge-availability/resource-type}}
                 (with-redefs [crud/retrieve-by-id-as-admin (constantly {:capabilities       [nb-utils/capability-heartbeat]
                                                                         :heartbeat-interval 20})]
                   (data-utils/nuvlabox-status->insert-availability-request nuvlabox-status false))))))
      (is (nil?
            (with-redefs [crud/retrieve-by-id-as-admin (constantly {:capabilities       [nb-utils/capability-heartbeat]
                                                                    :heartbeat-interval 20})]
              (data-utils/nuvlabox-status->insert-availability-request nuvlabox-status true)))))))

(deftest get-deployment-state
  (with-redefs [time/now (constantly (time/parse-date "2024-11-06T09:18:02.545Z"))]
    (is (= [{:name    "agent.image",
             :value   "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
             :node-id "agent"}
            {:name    "agent.node-id",
             :value   "agent",
             :node-id "agent"}
            {:name    "agent.service-id",
             :value   "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef",
             :node-id "agent"}]
           (t/get-deployment-state
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
  (is (= [{:name    "node_exporter.mode"
           :node-id "node_exporter"
           :value   "replicated"}
          {:name    "node_exporter.replicas.running"
           :node-id "node_exporter"
           :value   "1"}
          {:name    "node_exporter.replicas.desired"
           :node-id "node_exporter"
           :value   "1"}
          {:name    "node_exporter.service-id"
           :node-id "node_exporter"
           :value   "8zjrszesw70t"}
          {:name    "node_exporter.node-id"
           :node-id "node_exporter"
           :value   "node_exporter"}
          {:name    "node_exporter.image"
           :node-id "node_exporter"
           :value   "quay.io/prometheus/node-exporter:latest"}
          {:name    "otelcol.mode"
           :node-id "otelcol"
           :value   "replicated"}
          {:name    "otelcol.replicas.running"
           :node-id "otelcol"
           :value   "1"}
          {:name    "otelcol.replicas.desired"
           :node-id "otelcol"
           :value   "1"}
          {:name    "otelcol.service-id"
           :node-id "otelcol"
           :value   "uuip6ca6fwsn"}
          {:name    "otelcol.node-id"
           :node-id "otelcol"
           :value   "otelcol"}
          {:name    "otelcol.image"
           :node-id "otelcol"
           :value   "harbor.res.eng.it/icos/meta-kernel/observability/otel-collector-icos-distribution:1.2.0-v0.92.0-1"}
          {:name    "telemetruum_leaf_exporter.mode"
           :node-id "telemetruum_leaf_exporter"
           :value   "replicated"}
          {:name    "telemetruum_leaf_exporter.replicas.running"
           :node-id "telemetruum_leaf_exporter"
           :value   "1"}
          {:name    "telemetruum_leaf_exporter.replicas.desired"
           :node-id "telemetruum_leaf_exporter"
           :value   "1"}
          {:name    "telemetruum_leaf_exporter.service-id"
           :node-id "telemetruum_leaf_exporter"
           :value   "t5smdvbjrht7"}
          {:name    "telemetruum_leaf_exporter.node-id"
           :node-id "telemetruum_leaf_exporter"
           :value   "telemetruum_leaf_exporter"}
          {:name    "telemetruum_leaf_exporter.image"
           :node-id "telemetruum_leaf_exporter"
           :value   "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0"}]
         (t/get-deployment-state
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
                               :Spec          {:Name   "395a87fa-6b53-4e76-8a36-eccf8a19bc39_otelcol",
                                               :Labels {:com.docker.stack.image     "harbor.res.eng.it/icos/meta-kernel/observability/otel-collector-icos-distribution:1.2.0-v0.92.0-1",
                                                        :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                               :Mode   {:Replicated {:Replicas 1}}},
                               :ServiceStatus {:RunningTasks 1,
                                               :DesiredTasks 1}}
                              {:ID            "t5smdvbjrht7sd0cfki9nu4qj",
                               :Spec          {:Name   "395a87fa-6b53-4e76-8a36-eccf8a19bc39_telemetruum_leaf_exporter",
                                               :Labels {:com.docker.stack.image     "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0",
                                                        :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                               :Mode   {:Replicated {:Replicas 1}}},
                               :ServiceStatus {:RunningTasks 1,
                                               :DesiredTasks 1}}]}},
            :network       {:ips {:public "143.233.127.6",
                                  :swarm  "10.160.3.194",
                                  :vpn    "10.0.133.172",
                                  :local  "10.160.3.157"}}}))))

(deftest complete-param
  (with-redefs [time/now (constantly (time/parse-date "2024-11-06T10:36:22.306Z"))]
    (let [dep-id "deployment/xyz"]
      (is (= {:acl           {:edit-acl ["deployment/xyz"]
                              :owners   ["group/nuvla-admin"]}
              :created       "2024-11-06T10:36:22.306Z"
              :created-by    "internal"
              :id            "deployment-parameter/bfeb89ab-ebb8-38fb-b08d-1fabcf26ccfc"
              :name          "agent.image"
              :node-id       "agent"
              :parent        "deployment/xyz"
              :resource-type "deployment-parameter"
              :updated       "2024-11-06T10:36:22.306Z"
              :value         "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker"}
             (t/complete-param dep-id
                               {:name    "agent.image",
                                :value   "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
                                :node-id "agent"}))))))


(deftest param-bulk-operation-data
  (is (= [{:update {:_id    "deployment-parameter/bfeb89ab-ebb8-38fb-b08d-1fabcf26ccfc"
                    :_index "deployment-parameter"}}
          {:doc    {:value "some-value"}
           :upsert {:acl           {:edit-acl ["deployment/xyz"]
                                    :owners   ["group/nuvla-admin"]}
                    :created       "2024-11-06T10:36:22.306Z"
                    :created-by    "internal"
                    :id            "deployment-parameter/bfeb89ab-ebb8-38fb-b08d-1fabcf26ccfc"
                    :name          "agent.image"
                    :node-id       "agent"
                    :parent        "deployment/xyz"
                    :resource-type "deployment-parameter"
                    :updated       "2024-11-06T10:36:22.306Z"
                    :value         "some-value"}}]
         (t/param-bulk-operation-data {:acl           {:edit-acl ["deployment/xyz"]
                                                       :owners   ["group/nuvla-admin"]}
                                       :created       "2024-11-06T10:36:22.306Z"
                                       :created-by    "internal"
                                       :id            "deployment-parameter/bfeb89ab-ebb8-38fb-b08d-1fabcf26ccfc"
                                       :name          "agent.image"
                                       :node-id       "agent"
                                       :parent        "deployment/xyz"
                                       :resource-type "deployment-parameter"
                                       :updated       "2024-11-06T10:36:22.306Z"
                                       :value         "some-value"}))))

(deftest prepare-bulk-operation-data
  (is (= [{:update {:_id    "deployment-parameter/a85cebf7-17b0-324e-a2ec-a1143be6056d"
                    :_index "deployment-parameter"}}
          {:doc    {:value "quay.io/prometheus/node-exporter:latest"}
           :upsert {:id    "deployment-parameter/a85cebf7-17b0-324e-a2ec-a1143be6056d"
                    :name  "node_exporter.image"
                    :value "quay.io/prometheus/node-exporter:latest"}}
          {:update {:_id    "deployment-parameter/ea930503-cd39-369f-bc3f-e455f1ddf024"
                    :_index "deployment-parameter"}}
          {:doc    {:value "10.0.133.172"}
           :upsert {:id    "deployment-parameter/ea930503-cd39-369f-bc3f-e455f1ddf024"
                    :name  "hostname"
                    :value "10.0.133.172"}}]
         (t/params-bulk-operation-data [{:name  "node_exporter.image",
                                         :value "quay.io/prometheus/node-exporter:latest",
                                         :id    "deployment-parameter/a85cebf7-17b0-324e-a2ec-a1143be6056d"}
                                        {:name  "hostname",
                                         :value "10.0.133.172",
                                         :id    "deployment-parameter/ea930503-cd39-369f-bc3f-e455f1ddf024"}]))))

(deftest get-ne-deployment-params
  (with-redefs [time/now (constantly (time/parse-date "2024-11-06T09:18:02.545Z"))]
    (is (= [{:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/16f7d4ac-0b34-37e8-8cf3-dff5449e2712"
             :name          "ip.local"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         ""}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/a26e25ca-1367-32aa-aa03-83083c0e0887"
             :name          "ip.public"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "178.194.193.87"}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/bde74959-94aa-3599-b146-bac88c48d244"
             :name          "ip.swarm"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "192.168.64.4"}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/666dd1d0-cb68-32c0-97f5-51cd5c455b41"
             :name          "ip.vpn"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         ""}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/92871bb0-52ef-3509-9f8c-fe4d95a40157"
             :name          "hostname"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "178.194.193.87"}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/9d05074c-c797-3db4-aac3-c306d23face1"
             :name          "agent.image"
             :node-id       "agent"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker"}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/7379096e-a144-316a-9b13-ca433c32d0f0"
             :name          "agent.node-id"
             :node-id       "agent"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "agent"}
            {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/ad63df24-6ba8-3cca-a613-3ae283da2a15"
             :name          "agent.service-id"
             :node-id       "agent"
             :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}]
           (t/get-ne-deployment-params
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
                                             :Id     "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}]}}}
             [{:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
               :module {:compatibility "docker-compose"
                        :subtype       "application"}}])))
    (is (= [{:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/00e08780-9056-3bd6-a38f-88d3662d881f"
             :name          "ip.local"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "10.160.3.157"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/471cdd6f-0e90-3a76-baf8-77831eff75d9"
             :name          "ip.public"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "143.233.127.6"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/be6612c6-628e-3eab-9fba-81cef3ae7648"
             :name          "ip.swarm"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "10.160.3.194"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/1fefb4e4-b7bb-346b-92b9-acd3a3c00d1e"
             :name          "ip.vpn"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "10.0.133.172"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/ea930503-cd39-369f-bc3f-e455f1ddf024"
             :name          "hostname"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "10.0.133.172"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/11f8e5d0-6ca1-370e-9bf9-e7be1354ca61"
             :name          "node_exporter.mode"
             :node-id       "node_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "replicated"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/6f858e11-1aee-3a95-85ab-5e83af8bb828"
             :name          "node_exporter.replicas.running"
             :node-id       "node_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/679d66d0-631c-3a3d-9d6b-c901c47a0d81"
             :name          "node_exporter.replicas.desired"
             :node-id       "node_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/2ea2cf7d-75f3-3a66-ae58-fd71bbe8bebd"
             :name          "node_exporter.service-id"
             :node-id       "node_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "8zjrszesw70t"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/bd11f3f7-f962-3556-97f5-3335cdb8c58e"
             :name          "node_exporter.node-id"
             :node-id       "node_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "node_exporter"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/a85cebf7-17b0-324e-a2ec-a1143be6056d"
             :name          "node_exporter.image"
             :node-id       "node_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "quay.io/prometheus/node-exporter:latest"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/49553853-8843-35a4-b6a0-f29e33fa5d2b"
             :name          "otelcol.mode"
             :node-id       "otelcol"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "replicated"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/41d1dc8f-e40c-379b-8f90-97a5449bb599"
             :name          "otelcol.replicas.running"
             :node-id       "otelcol"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/758b85b3-7212-3c75-8833-7861a53b4ead"
             :name          "otelcol.replicas.desired"
             :node-id       "otelcol"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/956fd55c-a1ec-3a34-9e7d-915a2dfa0884"
             :name          "otelcol.service-id"
             :node-id       "otelcol"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "uuip6ca6fwsn"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/77b44cf0-ba8c-380f-847d-2d12fefd88c6"
             :name          "otelcol.node-id"
             :node-id       "otelcol"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "otelcol"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/66ef5e70-34ca-34c9-838c-a1b05ddd8b34"
             :name          "otelcol.image"
             :node-id       "otelcol"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "harbor.res.eng.it/icos/meta-kernel/observability/otel-collector-icos-distribution:1.2.0-v0.92.0-1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/8388dd76-0097-3dc3-a5c1-04eb53dc6f26"
             :name          "telemetruum_leaf_exporter.mode"
             :node-id       "telemetruum_leaf_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "replicated"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/45c8a074-2a03-37ce-9cf8-6d8a88943940"
             :name          "telemetruum_leaf_exporter.replicas.running"
             :node-id       "telemetruum_leaf_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/9b89bb0b-98cb-35cf-9b1b-9e73ef8ecbe6"
             :name          "telemetruum_leaf_exporter.replicas.desired"
             :node-id       "telemetruum_leaf_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "1"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/f4286a03-43f5-3dc8-8878-0231398cc635"
             :name          "telemetruum_leaf_exporter.service-id"
             :node-id       "telemetruum_leaf_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "t5smdvbjrht7"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/9e969e18-442d-36d7-9907-dc8680d373b5"
             :name          "telemetruum_leaf_exporter.node-id"
             :node-id       "telemetruum_leaf_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "telemetruum_leaf_exporter"}
            {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                             :owners   ["group/nuvla-admin"]}
             :created       "2024-11-06T09:18:02.545Z"
             :created-by    "internal"
             :id            "deployment-parameter/8e9ad322-5016-365c-aa40-ad75fc485a75"
             :name          "telemetruum_leaf_exporter.image"
             :node-id       "telemetruum_leaf_exporter"
             :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
             :resource-type "deployment-parameter"
             :updated       "2024-11-06T09:18:02.545Z"
             :value         "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0"}]
           (t/get-ne-deployment-params
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
                                 :Spec          {:Name   "395a87fa-6b53-4e76-8a36-eccf8a19bc39_otelcol",
                                                 :Labels {:com.docker.stack.image     "harbor.res.eng.it/icos/meta-kernel/observability/otel-collector-icos-distribution:1.2.0-v0.92.0-1",
                                                          :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                                 :Mode   {:Replicated {:Replicas 1}}},
                                 :ServiceStatus {:RunningTasks 1,
                                                 :DesiredTasks 1}}
                                {:ID            "t5smdvbjrht7sd0cfki9nu4qj",
                                 :Spec          {:Name   "395a87fa-6b53-4e76-8a36-eccf8a19bc39_telemetruum_leaf_exporter",
                                                 :Labels {:com.docker.stack.image     "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0",
                                                          :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                                 :Mode   {:Replicated {:Replicas 1}}},
                                 :ServiceStatus {:RunningTasks 1,
                                                 :DesiredTasks 1}}]}},
              :network       {:ips {:public "143.233.127.6",
                                    :swarm  "10.160.3.194",
                                    :vpn    "10.0.133.172",
                                    :local  "10.160.3.157"}}}
             [{:id     "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
               :module {:compatibility "swarm"
                        :subtype       "application"}}])))))


;; to build a list of deployments states to be updated
;; do not start from coe-resources but from deployment
;; because compose apps has only a uuid registered as label "com.docker.compose.project".
;; so for each deployment depending on type dig into coe resources and status to get values to update them afterward
;; it has also the advantage to remove the need to check if deployment is really running on this NE

;; query deployments in state started
;; for each of deployment running on the NE depending on type check docker containers/services or kubernetes resources
;; build a bulk update query on parameters

