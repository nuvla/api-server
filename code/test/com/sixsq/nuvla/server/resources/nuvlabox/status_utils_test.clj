(ns com.sixsq.nuvla.server.resources.nuvlabox.status-utils-test
  (:require
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.es.common.utils :as escu]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.nuvlabox.data-utils :as data-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.status-utils :as t]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.resources.spec.module :as module-spec]
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

(def nb-status-coe-kubernetes {:ip            "10.0.133.172",
                               :coe-resources {:kubernetes
                                               {:deployments  [{:metadata
                                                                {:labels
                                                                 {:nuvla.deployment.uuid "819b9a9e-010f-4c26-82d5-aa395bbb6179"},
                                                                 :name "hello-edge"},
                                                                :spec   {:replicas 1},
                                                                :status {:replicas 1}},
                                                               {:metadata {:name      "release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world",
                                                                           :namespace "fd9d09a2-4ce8-4fbc-a112-0f60a46064ee",},
                                                                :spec     {:replicas 1},
                                                                :status   {:replicas 1}}],
                                                :services     [{:metadata {:name      "release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world",
                                                                           :namespace "fd9d09a2-4ce8-4fbc-a112-0f60a46064ee"},
                                                                :spec     {:ports [{:port        80,
                                                                                    :target_port 80,
                                                                                    :node_port   30007,
                                                                                    :protocol    "TCP"}]}}],
                                                :helmreleases [{:name        "nuvlabox-6c37d85d-d818-4069-a50c-dae9983364d1",
                                                                :namespace   "default",
                                                                :revision    "1",
                                                                :updated     "2024-11-19 09:41:32.121871 +0100 +0100",
                                                                :status      "deployed",
                                                                :chart       "nuvlaedge-2.17.0",
                                                                :app_version "2.17.0"}
                                                               {:name        "release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee",
                                                                :namespace   "fd9d09a2-4ce8-4fbc-a112-0f60a46064ee",
                                                                :revision    "1",
                                                                :updated     "2024-11-13 11:13:42.534312596 +0000 UTC",
                                                                :status      "deployed",
                                                                :chart       "hello-world-0.1.0",
                                                                :app_version "1.16.0"}]}},
                               :network       {:ips {:public "143.233.127.6",
                                                     :swarm  "10.160.3.194",
                                                     :vpn    "10.0.133.172",
                                                     :local  "10.160.3.157"}}})

(def nb-status-coe-docker-swarm {:ip            "10.0.133.172",
                                 :coe-resources {:docker
                                                 {:services
                                                  [{:ID            "8zjrszesw70t88thnna2eehxw",
                                                    :Spec          {:Name   "395a87fa-6b53-4e76-8a36-eccf8a19bc39_node_exporter",
                                                                    :Labels {:com.docker.stack.image     "quay.io/prometheus/node-exporter:latest",
                                                                             :com.docker.stack.namespace "395a87fa-6b53-4e76-8a36-eccf8a19bc39"},
                                                                    :Mode   {:Replicated {:Replicas 1}}},
                                                    :Endpoint      {:Ports [{:Protocol      "tcp",
                                                                             :TargetPort    9200,
                                                                             :PublishedPort 9200,
                                                                             :PublishMode   "ingress"}]}
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
                                                       :local  "10.160.3.157"}}})

(def nb-status-coe-docker-compose {:id            "nuvlabox-status/fb4da83b-e911-4f01-8bed-82a8473ac8e3",
                                   :ip            "178.194.193.87",
                                   :network       {:ips
                                                   {:public "178.194.193.87",
                                                    :swarm  "192.168.64.4",
                                                    :vpn    "",
                                                    :local  ""}}
                                   :coe-resources {:docker
                                                   {:containers [{:Id "f271e2566489ab4beca2ac193935076f8cf89b5ab768c78962a4ea028c9ab51c"},
                                                                 {:Image  "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
                                                                  :Ports  [{:IP          "0.0.0.0",
                                                                            :PrivatePort 8000
                                                                            :Type        "tcp"}
                                                                           {:IP          "0.0.0.0",
                                                                            :PrivatePort 80
                                                                            :PublicPort  32786
                                                                            :Type        "tcp"}
                                                                           {:IP          "0.0.0.0",
                                                                            :PrivatePort 8080
                                                                            :PublicPort  33783
                                                                            :Type        "udp"}]
                                                                  :Labels {:com.docker.compose.service "agent",
                                                                           :com.docker.compose.project "b3b70820-2de4-4a11-b00c-a79661c3d433"},
                                                                  :Id     "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}]}}})

(deftest get-deployment-state
  (with-redefs [time/now (constantly (time/parse-date "2024-11-06T09:18:02.545Z"))]
    (is (= #{{:name    "agent.image"
              :node-id "agent"
              :value   "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker"}
             {:name    "agent.node-id"
              :node-id "agent"
              :value   "agent"}
             {:name    "agent.service-id"
              :node-id "agent"
              :value   "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}
             {:name    "agent.tcp.80"
              :node-id "agent"
              :value   "32786"}
             {:name    "agent.udp.8080"
              :node-id "agent"
              :value   "33783"}}
           (set (t/get-deployment-state
                  {:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                   :module {:compatibility "docker-compose"
                            :subtype       "application"}}
                  nb-status-coe-docker-compose)))))
  (is (= #{{:name    "node_exporter.mode"
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
           {:name    "node_exporter.tcp.9200"
            :node-id "node_exporter"
            :value   "9200"}
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
            :value   "harbor.res.eng.it/icos/meta-kernel/observability/telemetruum/telemetruum-leaf-exporter:0.1.0"}}
         (set (t/get-deployment-state
                {:id     "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                 :module {:compatibility "swarm"
                          :subtype       "application"}}
                nb-status-coe-docker-swarm))))
  (is (= #{{:name    "Deployment.hello-edge.replicas.desired"
            :node-id "Deployment.hello-edge"
            :value   "1"}
           {:name    "Deployment.hello-edge.node-id"
            :node-id "Deployment.hello-edge"
            :value   "Deployment.hello-edge"}
           {:name    "Deployment.hello-edge.replicas.running"
            :node-id "Deployment.hello-edge"
            :value   "0"}}
         (set (t/get-deployment-state
                {:id     "deployment/819b9a9e-010f-4c26-82d5-aa395bbb6179"
                 :module {:subtype module-spec/subtype-app-k8s}}
                nb-status-coe-kubernetes))))

  (is (= #{{:name    "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world.node-id"
            :node-id "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"
            :value   "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"}
           {:name    "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world.replicas.desired"
            :node-id "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"
            :value   "1"}
           {:name    "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world.replicas.running"
            :node-id "Deployment.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"
            :value   "0"}
           {:name    "Service.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world.node-id"
            :node-id "Service.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"
            :value   "Service.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"}
           {:name    "Service.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world.tcp.80"
            :node-id "Service.release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee-hello-world"
            :value   "30007"}
           {:name  "helm-app_version"
            :value "1.16.0"}
           {:name  "helm-chart"
            :value "hello-world-0.1.0"}
           {:name  "helm-name"
            :value "release-fd9d09a2-4ce8-4fbc-a112-0f60a46064ee"}
           {:name  "helm-namespace"
            :value "fd9d09a2-4ce8-4fbc-a112-0f60a46064ee"}
           {:name  "helm-revision"
            :value "1"}
           {:name  "helm-status"
            :value "deployed"}
           {:name  "helm-updated"
            :value "2024-11-13 11:13:42.534312596 +0000 UTC"}}
         (set (t/get-deployment-state
                {:id     "deployment/fd9d09a2-4ce8-4fbc-a112-0f60a46064ee"
                 :module {:subtype module-spec/subtype-app-helm}}
                nb-status-coe-kubernetes)))))

(deftest complete-param
  (with-redefs [time/now (constantly (time/parse-date "2024-11-06T10:36:22.306Z"))]
    (let [deployment {:id  "deployment/xyz"
                      :acl {:owners ["user/someone"]}}]
      (is (= {:acl           {:owners ["user/someone"]}
              :created       "2024-11-06T10:36:22.306Z"
              :created-by    "internal"
              :id            "deployment-parameter/bfeb89ab-ebb8-38fb-b08d-1fabcf26ccfc"
              :name          "agent.image"
              :node-id       "agent"
              :parent        "deployment/xyz"
              :resource-type "deployment-parameter"
              :updated       "2024-11-06T10:36:22.306Z"
              :value         "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker"}
             (t/complete-param deployment
                               {:name    "agent.image",
                                :value   "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
                                :node-id "agent"}))))))

(deftest param-bulk-operation-data
  (is (= [{:update {:_id    "bfeb89ab-ebb8-38fb-b08d-1fabcf26ccfc"
                    :_index "nuvla-deployment-parameter"}}
          {:doc    {:updated "2024-11-06T10:36:22.306Z"
                    :value   "some-value"}
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
  (is (= [{:update {:_id    "a85cebf7-17b0-324e-a2ec-a1143be6056d"
                    :_index "nuvla-deployment-parameter"}}
          {:doc    {:value "quay.io/prometheus/node-exporter:latest"}
           :upsert {:id    "deployment-parameter/a85cebf7-17b0-324e-a2ec-a1143be6056d"
                    :name  "node_exporter.image"
                    :value "quay.io/prometheus/node-exporter:latest"}}
          {:update {:_id    "ea930503-cd39-369f-bc3f-e455f1ddf024"
                    :_index "nuvla-deployment-parameter"}}
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
    (testing "docker compose NuvlaEdge deployment parameters"
      (is (= #{{:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
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
                :value         "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}
               {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                                :owners   ["group/nuvla-admin"]}
                :created       "2024-11-06T09:18:02.545Z"
                :created-by    "internal"
                :id            "deployment-parameter/277d524d-ed85-3ac9-842f-aefa5f429add"
                :name          "agent.tcp.80"
                :node-id       "agent"
                :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                :resource-type "deployment-parameter"
                :updated       "2024-11-06T09:18:02.545Z"
                :value         "32786"}
               {:acl           {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                                :owners   ["group/nuvla-admin"]}
                :created       "2024-11-06T09:18:02.545Z"
                :created-by    "internal"
                :id            "deployment-parameter/7832efc3-e60b-3ba7-8164-145e33da0b3c"
                :name          "agent.udp.8080"
                :node-id       "agent"
                :parent        "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                :resource-type "deployment-parameter"
                :updated       "2024-11-06T09:18:02.545Z"
                :value         "33783"}}
             (set (t/get-ne-deployment-params
                    nb-status-coe-docker-compose
                    [{:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                      :module {:compatibility "docker-compose"
                               :subtype       "application"}
                      :acl    {:edit-acl ["deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"]
                               :owners   ["group/nuvla-admin"]}}])))))))

(deftest partition-by-old-docker-for-swarm
  (let [dep-compose           {:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                               :module {:compatibility "docker-compose"
                                        :subtype       "application"}}
        dep-swarm-not-defined {:id     "deployment/swarm"
                               :module {:compatibility "swarm"
                                        :subtype       "application"}}
        dep-swarm-defined     {:id     "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                               :module {:compatibility "swarm"
                                        :subtype       "application"}}]
    (testing "docker compose goes normal process"
      (is (= [[]
              [dep-compose]]
             (t/partition-by-old-docker-for-swarm
               nb-status-coe-docker-compose
               [dep-compose]))))
    (testing "swarm without corresponding desired-tasks should be detected as old docker"
      (is (= [[dep-swarm-not-defined]
              [dep-compose]]
             (t/partition-by-old-docker-for-swarm
               nb-status-coe-docker-compose
               [dep-compose
                dep-swarm-not-defined]))))
    (testing "fictive case to test if split is working fine"
      (is (= [[dep-swarm-not-defined]
              [dep-swarm-defined]]
             (t/partition-by-old-docker-for-swarm
               nb-status-coe-docker-swarm
               [dep-swarm-defined
                dep-swarm-not-defined]))))))

(use-fixtures :once ltu/with-test-server-fixture)

(deftest update-deployment-parameters
  (testing "load test"
    (let [n              100
          defined-uuids  (take n (repeatedly random-uuid))
          summary-fn     escu/summarise-bulk-operation-response
          ne-deployments (map (fn [uuid]
                                {:acl           {:edit-acl ["deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"]
                                                 :owners   ["group/nuvla-admin"]}
                                 :created       "2024-11-06T09:18:02.545Z"
                                 :created-by    "internal"
                                 :module        {:subtype       module-spec/subtype-app-docker
                                                 :compatibility module-spec/compatibility-docker-compose}
                                 :id            (str "deployment-parameter/" uuid)
                                 :name          "param-name"
                                 :parent        "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                                 :resource-type "deployment-parameter"
                                 :updated       "2024-11-06T09:18:02.545Z"
                                 :value         "v"}) defined-uuids)
          nb-status      {:coe-resources {:docker {}}}
          result!        (atom nil)]
      (with-redefs [escu/summarise-bulk-operation-response #(reset! result! %)]
        (testing "without coe-resources no params should be created"
          (t/update-deployment-parameters {} ne-deployments)
          (is (nil? @result!)))
        (reset! result! nil)

        (testing "with coe-resources params should be created"
          (t/update-deployment-parameters nb-status ne-deployments)
          (is (re-matches (re-pattern (str "errors: false took: \\d{1,3}ms created: " (* 5 n))) (summary-fn @result!))))
        (reset! result! nil)

        (testing "with coe-resources params should be updated"
          (t/update-deployment-parameters nb-status ne-deployments)
          (is (re-matches (re-pattern (str "errors: false took: \\d{1,3}ms updated: " (* 5 n))) (summary-fn @result!))))
        (reset! result! nil)

        (testing "when something wrong happens, error is logged and no exception is thrown and nb-status is returned"
          (with-redefs [t/param-bulk-operation-data (constantly "wrong")]
            (is (= (t/update-deployment-parameters nb-status ne-deployments) nb-status))))))))


(deftest create-deployment-state-jobs
  (let [results!   (atom [])
        dep-docker {:id             "deployment-parameter/abc"
                    :execution-mode "pull"
                    :module         {:subtype module-spec/subtype-app-docker}
                    :parent         "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                    :resource-type  "deployment-parameter"}
        dep-k8s    {:id             "deployment-parameter/abc"
                    :execution-mode "pull"
                    :module         {:subtype module-spec/subtype-app-k8s}
                    :parent         "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                    :resource-type  "deployment-parameter"}
        dep-helm   {:id             "deployment-parameter/abc"
                    :execution-mode "push"
                    :module         {:subtype module-spec/subtype-app-helm}
                    :parent         "deployment/395a87fa-6b53-4e76-8a36-eccf8a19bc39"
                    :resource-type  "deployment-parameter"}]
    (testing "should create jobs since coe-resources is empty"
      (with-redefs [job-utils/create-job (fn [& args] (swap! results! conj args))]
        (t/create-deployment-state-jobs {} [dep-docker dep-k8s dep-helm]))
      (is (= @results! ['("deployment-parameter/abc"
                           "deployment_state"
                           {:owners ["group/nuvla-admin"]}
                           "internal"
                           :execution-mode
                           "pull")
                        '("deployment-parameter/abc"
                           "deployment_state"
                           {:owners ["group/nuvla-admin"]}
                           "internal"
                           :execution-mode
                           "pull")
                        '("deployment-parameter/abc"
                           "deployment_state"
                           {:owners ["group/nuvla-admin"]}
                           "internal"
                           :execution-mode
                           "push")])))
    (reset! results! [])

    (testing "should not create jobs since both (docker, k8s) coe-resources are there"
      (with-redefs [job-utils/create-job (fn [& args] (swap! results! conj args))]
        (t/create-deployment-state-jobs {:coe-resources {:docker     {:images []}
                                                         :kubernetes {:foo "bar"}}} [dep-docker dep-k8s dep-helm]))
      (is (= @results! [])))

    (reset! results! [])

    (testing "should create jobs only for k8s since coe-resources for docker are there"
      (with-redefs [job-utils/create-job (fn [& args] (swap! results! conj args))]
        (t/create-deployment-state-jobs {:coe-resources {:docker {:images []}}} [dep-docker dep-k8s dep-helm]))
      (is (= @results! ['("deployment-parameter/abc"
                           "deployment_state"
                           {:owners ["group/nuvla-admin"]}
                           "internal"
                           :execution-mode
                           "pull")
                        '("deployment-parameter/abc"
                           "deployment_state"
                           {:owners ["group/nuvla-admin"]}
                           "internal"
                           :execution-mode
                           "push")])))))
