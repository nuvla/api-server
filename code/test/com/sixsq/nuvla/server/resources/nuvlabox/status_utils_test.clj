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
                                                                :app_version "1.16.0"}]
                                                :pods         [{:spec
                                                                {:overhead                         nil,
                                                                 :automount_service_account_token  nil,
                                                                 :restart_policy                   "Always",
                                                                 :set_hostname_as_fqdn             nil,
                                                                 :host_users                       nil,
                                                                 :ephemeral_containers             nil,
                                                                 :dns_policy                       "ClusterFirst",
                                                                 :security_context
                                                                 {:sysctls                nil,
                                                                  :run_as_non_root        nil,
                                                                  :fs_group               nil,
                                                                  :windows_options        nil,
                                                                  :run_as_user            nil,
                                                                  :supplemental_groups    nil,
                                                                  :fs_group_change_policy nil,
                                                                  :se_linux_options       nil,
                                                                  :seccomp_profile        nil,
                                                                  :run_as_group           nil},
                                                                 :host_network                     nil,
                                                                 :scheduling_gates                 nil,
                                                                 :termination_grace_period_seconds 30,
                                                                 :priority_class_name              nil,
                                                                 :readiness_gates                  nil,
                                                                 :node_selector                    nil,
                                                                 :preemption_policy                "PreemptLowerPriority",
                                                                 :service_account                  "default",
                                                                 :host_aliases                     nil,
                                                                 :affinity                         nil,
                                                                 :image_pull_secrets               nil,
                                                                 :hostname                         nil,
                                                                 :containers
                                                                 [{:args                       nil,
                                                                   :stdin                      nil,
                                                                   :startup_probe              nil,
                                                                   :tty                        nil,
                                                                   :security_context           nil,
                                                                   :volume_mounts
                                                                   [{:name              "kube-api-access-jxzwj",
                                                                     :sub_path          nil,
                                                                     :read_only         true,
                                                                     :mount_propagation nil,
                                                                     :sub_path_expr     nil,
                                                                     :mount_path        "/var/run/secrets/kubernetes.io/serviceaccount"}],
                                                                   :name                       "nginx",
                                                                   :stdin_once                 nil,
                                                                   :env_from                   nil,
                                                                   :command                    nil,
                                                                   :lifecycle                  nil,
                                                                   :image_pull_policy          "Always",
                                                                   :env                        nil,
                                                                   :working_dir                nil,
                                                                   :termination_message_policy "File",
                                                                   :ports
                                                                   [{:protocol       "TCP",
                                                                     :name           nil,
                                                                     :host_port      nil,
                                                                     :container_port 80,
                                                                     :host_ip        nil}],
                                                                   :termination_message_path   "/dev/termination-log",
                                                                   :image                      "nginx:latest",
                                                                   :readiness_probe            nil,
                                                                   :volume_devices             nil,
                                                                   :resources                  {:claims nil, :requests nil, :limits nil},
                                                                   :liveness_probe             nil}],
                                                                 :share_process_namespace          nil,
                                                                 :volumes
                                                                 [{:config_map              nil,
                                                                   :gce_persistent_disk     nil,
                                                                   :glusterfs               nil,
                                                                   :storageos               nil,
                                                                   :flex_volume             nil,
                                                                   :ephemeral               nil,
                                                                   :azure_disk              nil,
                                                                   :projected
                                                                   {:sources
                                                                    [{:config_map   nil,
                                                                      :secret       nil,
                                                                      :service_account_token
                                                                      {:path "token", :audience nil, :expiration_seconds 3607},
                                                                      :downward_api nil}
                                                                     {:config_map
                                                                      {:name     "kube-root-ca.crt",
                                                                       :optional nil,
                                                                       :items    [{:path "ca.crt", :key "ca.crt", :mode nil}]},
                                                                      :secret                nil,
                                                                      :service_account_token nil,
                                                                      :downward_api          nil}
                                                                     {:config_map            nil,
                                                                      :secret                nil,
                                                                      :service_account_token nil,
                                                                      :downward_api
                                                                      {:items
                                                                       [{:path               "namespace",
                                                                         :mode               nil,
                                                                         :field_ref
                                                                         {:field_path "metadata.namespace", :api_version "v1"},
                                                                         :resource_field_ref nil}]}}],
                                                                    :default_mode 420},
                                                                   :rbd                     nil,
                                                                   :nfs                     nil,
                                                                   :flocker                 nil,
                                                                   :portworx_volume         nil,
                                                                   :name                    "kube-api-access-jxzwj",
                                                                   :empty_dir               nil,
                                                                   :quobyte                 nil,
                                                                   :azure_file              nil,
                                                                   :iscsi                   nil,
                                                                   :secret                  nil,
                                                                   :cinder                  nil,
                                                                   :csi                     nil,
                                                                   :vsphere_volume          nil,
                                                                   :cephfs                  nil,
                                                                   :host_path               nil,
                                                                   :aws_elastic_block_store nil,
                                                                   :git_repo                nil,
                                                                   :photon_persistent_disk  nil,
                                                                   :persistent_volume_claim nil,
                                                                   :fc                      nil,
                                                                   :scale_io                nil,
                                                                   :downward_api            nil}],
                                                                 :subdomain                        nil,
                                                                 :resource_claims                  nil,
                                                                 :runtime_class_name               nil,
                                                                 :priority                         0,
                                                                 :scheduler_name                   "default-scheduler",
                                                                 :active_deadline_seconds          nil,
                                                                 :node_name                        "minikube",
                                                                 :host_ipc                         nil,
                                                                 :enable_service_links             true,
                                                                 :os                               nil,
                                                                 :dns_config                       nil,
                                                                 :init_containers                  nil,
                                                                 :host_pid                         nil,
                                                                 :topology_spread_constraints      nil,
                                                                 :tolerations
                                                                 [{:key                "node.kubernetes.io/not-ready",
                                                                   :value              nil,
                                                                   :toleration_seconds 300,
                                                                   :operator           "Exists",
                                                                   :effect             "NoExecute"}
                                                                  {:key                "node.kubernetes.io/unreachable",
                                                                   :value              nil,
                                                                   :toleration_seconds 300,
                                                                   :operator           "Exists",
                                                                   :effect             "NoExecute"}],
                                                                 :service_account_name             "default"},
                                                                :status
                                                                {:nominated_node_name          nil,
                                                                 :pod_ip                       "10.244.0.34",
                                                                 :qos_class                    "BestEffort",
                                                                 :start_time                   "2025-07-01T16:34:38+00:00",
                                                                 :phase                        "Running",
                                                                 :ephemeral_container_statuses nil,
                                                                 :reason                       nil,
                                                                 :pod_i_ps                     [{:ip "10.244.0.34"}],
                                                                 :conditions
                                                                 [{:last_transition_time "2025-07-01T16:34:48+00:00",
                                                                   :type                 "PodReadyToStartContainers",
                                                                   :reason               nil,
                                                                   :status               "True",
                                                                   :last_probe_time      nil,
                                                                   :message              nil}
                                                                  {:last_transition_time "2025-07-01T16:34:38+00:00",
                                                                   :type                 "Initialized",
                                                                   :reason               nil,
                                                                   :status               "True",
                                                                   :last_probe_time      nil,
                                                                   :message              nil}
                                                                  {:last_transition_time "2025-07-01T16:34:48+00:00",
                                                                   :type                 "Ready",
                                                                   :reason               nil,
                                                                   :status               "True",
                                                                   :last_probe_time      nil,
                                                                   :message              nil}
                                                                  {:last_transition_time "2025-07-01T16:34:48+00:00",
                                                                   :type                 "ContainersReady",
                                                                   :reason               nil,
                                                                   :status               "True",
                                                                   :last_probe_time      nil,
                                                                   :message              nil}
                                                                  {:last_transition_time "2025-07-01T16:34:38+00:00",
                                                                   :type                 "PodScheduled",
                                                                   :reason               nil,
                                                                   :status               "True",
                                                                   :last_probe_time      nil,
                                                                   :message              nil}],
                                                                 :container_statuses
                                                                 [{:started       true,
                                                                   :ready         true,
                                                                   :name          "nginx",
                                                                   :state
                                                                   {:running    {:started_at "2025-07-01T16:34:47+00:00"},
                                                                    :waiting    nil,
                                                                    :terminated nil},
                                                                   :last_state    {:running nil, :waiting nil, :terminated nil},
                                                                   :restart_count 0,
                                                                   :image         "nginx:latest",
                                                                   :container_id
                                                                   "docker://90a431e48ada2b028edc0c03ea138df38d35ce90943beb0c7a287db40efb2e9b",
                                                                   :image_id
                                                                   "docker-pullable://nginx@sha256:93230cd54060f497430c7a120e2347894846a81b6a5dd2110f7362c5423b4abc"}],
                                                                 :host_ip                      "192.168.49.2",
                                                                 :message                      nil,
                                                                 :init_container_statuses      nil},
                                                                :kind        nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels
                                                                 {:pod-template-hash      "77d9cd8fb4",
                                                                  :nuvla.application.name "819b9a9e-010f-4c26-82d5-aa395bbb6179",
                                                                  :nuvla.deployment.uuid  "819b9a9e-010f-4c26-82d5-aa395bbb6179",
                                                                  :app                    "nginx"},
                                                                 :generation                    1,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "a9f0929d-a15b-4b73-94f8-a826176a3e8e",
                                                                 :name                          "nginx-deployment-77d9cd8fb4-qm89s",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references
                                                                 [{:controller           true,
                                                                   :uid                  "e264256c-bc65-46e9-a8bf-954cba0f81c3",
                                                                   :name                 "nginx-deployment-77d9cd8fb4",
                                                                   :kind                 "ReplicaSet",
                                                                   :api_version          "apps/v1",
                                                                   :block_owner_deletion true}],
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 "nginx-deployment-77d9cd8fb4-",
                                                                 :creation_timestamp            "2025-07-01T16:34:38+00:00",
                                                                 :namespace                     "188d515b-6bac-4732-a16e-87e8cbda249f",
                                                                 :resource_version              "14992"}}]
                                                :configmaps   [{:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "fc87fcee-5e6f-4977-81bb-33b9f354f80d",
                                                                 :name                          "extension-apiserver-authentication",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:15+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "22"},
                                                                :data
                                                                {:requestheader-extra-headers-prefix "",
                                                                 :requestheader-group-headers        "",
                                                                 :requestheader-client-ca-file       "",
                                                                 :requestheader-allowed-names        "",
                                                                 :client-ca-file                     "",
                                                                 :requestheader-username-headers     ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "0ec55192-878e-4e19-bc4f-c2e88498c595",
                                                                 :name                          "kube-apiserver-legacy-service-account-token-tracking",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:15+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "45"},
                                                                :data        {:since ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "7a4fe685-5a15-4fae-bdd6-e9fa8d6ab45e",
                                                                 :name                          "cluster-info",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:16+00:00",
                                                                 :namespace                     "kube-public",
                                                                 :resource_version              "358"},
                                                                :data        {:kubeconfig "", :jws-kubeconfig-02zlb8 ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "85747ff2-0bb9-432d-ade6-2a1a976c2f89",
                                                                 :name                          "kubeadm-config",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:16+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "246"},
                                                                :data        {:ClusterConfiguration ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "ccaa77c9-beae-4989-af8d-5ba6fbafdb32",
                                                                 :name                          "kubelet-config",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:16+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "249"},
                                                                :data        {:kubelet ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "f4941bcb-5df0-4617-a90c-de5a24884e15",
                                                                 :name                          "coredns",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:17+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "263"},
                                                                :data        {:Corefile ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        {:app "kube-proxy"},
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "cfdf56db-26e2-4df2-b974-6b70e17eb4a6",
                                                                 :name                          "kube-proxy",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:17+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "291"},
                                                                :data        {:kubeconfig.conf "", :config.conf ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "33ac23f6-373d-48ef-8985-ec5fc5219219",
                                                                 :name                          "kube-root-ca.crt",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations
                                                                 {:description
                                                                  "Contains a CA bundle that can be used to verify the kube-apiserver when using internal endpoints such as the internal service IP or kubernetes.default.svc. No other usage is guaranteed across distributions of Kubernetes clusters."},
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:23+00:00",
                                                                 :namespace                     "default",
                                                                 :resource_version              "362"},
                                                                :data        {:ca.crt ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "adb7bdd6-3deb-41fa-9f81-608fc104a2bf",
                                                                 :name                          "kube-root-ca.crt",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations
                                                                 {:description
                                                                  "Contains a CA bundle that can be used to verify the kube-apiserver when using internal endpoints such as the internal service IP or kubernetes.default.svc. No other usage is guaranteed across distributions of Kubernetes clusters."},
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:23+00:00",
                                                                 :namespace                     "kube-node-lease",
                                                                 :resource_version              "363"},
                                                                :data        {:ca.crt ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "246abc98-0eba-446a-b26d-7e95113f4cc0",
                                                                 :name                          "kube-root-ca.crt",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations
                                                                 {:description
                                                                  "Contains a CA bundle that can be used to verify the kube-apiserver when using internal endpoints such as the internal service IP or kubernetes.default.svc. No other usage is guaranteed across distributions of Kubernetes clusters."},
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:23+00:00",
                                                                 :namespace                     "kube-public",
                                                                 :resource_version              "364"},
                                                                :data        {:ca.crt ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "1a7e1104-c349-490b-8769-8f6eabd56759",
                                                                 :name                          "kube-root-ca.crt",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations
                                                                 {:description
                                                                  "Contains a CA bundle that can be used to verify the kube-apiserver when using internal endpoints such as the internal service IP or kubernetes.default.svc. No other usage is guaranteed across distributions of Kubernetes clusters."},
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:23+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "365"},
                                                                :data        {:ca.crt ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "87340dd0-472a-4c55-877d-44a6906c4054",
                                                                 :name                          "kube-root-ca.crt",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations
                                                                 {:description
                                                                  "Contains a CA bundle that can be used to verify the kube-apiserver when using internal endpoints such as the internal service IP or kubernetes.default.svc. No other usage is guaranteed across distributions of Kubernetes clusters."},
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T16:27:32+00:00",
                                                                 :namespace                     "nuvlabox-3ba7df6c-369f-4cbe-95a6-950d06218a3b",
                                                                 :resource_version              "14359"},
                                                                :data        {:ca.crt ""}}
                                                               {:binary_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "44eeff89-8b01-45aa-85dc-1211e5209a22",
                                                                 :name                          "kube-root-ca.crt",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations
                                                                 {:description
                                                                  "Contains a CA bundle that can be used to verify the kube-apiserver when using internal endpoints such as the internal service IP or kubernetes.default.svc. No other usage is guaranteed across distributions of Kubernetes clusters."},
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T16:34:38+00:00",
                                                                 :namespace                     "188d515b-6bac-4732-a16e-87e8cbda249f",
                                                                 :resource_version              "14952"},
                                                                :data        {:ca.crt ""}}],
                                                :images       [{:names
                                                                ["alpine/socat:latest"
                                                                 "alpine/socat@sha256:6f6b7799b7280bc128cfbd55aae1deca507b71c7371969c62c2773302f1834ce"],
                                                                :size_bytes 10058623}
                                                               {:names
                                                                ["busybox:latest"
                                                                 "busybox@sha256:f85340bf132ae937d2c2a763b8335c9bab35d6e8293f70f606b9c6178d84f42b"],
                                                                :size_bytes 4042190}
                                                               {:names
                                                                ["curlimages/curl:latest"
                                                                 "curlimages/curl@sha256:9a1ed35addb45476afa911696297f8e115993df459278ed036182dd2cd22b67b"],
                                                                :size_bytes 24437827}
                                                               {:names
                                                                ["gcr.io/k8s-minikube/storage-provisioner:v5"
                                                                 "gcr.io/k8s-minikube/storage-provisioner@sha256:18eb69d1418e854ad5a19e399310e52808a8321e4c441c1dddad8977a0d7a944"],
                                                                :size_bytes 29032448}
                                                               {:names
                                                                ["nginx:latest"
                                                                 "nginx@sha256:93230cd54060f497430c7a120e2347894846a81b6a5dd2110f7362c5423b4abc"],
                                                                :size_bytes 197642500}
                                                               {:names
                                                                ["registry.k8s.io/coredns/coredns:v1.12.0"
                                                                 "registry.k8s.io/coredns/coredns@sha256:40384aa1f5ea6bfdc77997d243aec73da05f27aed0c5e9d65bfa98933c519d97"],
                                                                :size_bytes 68392336}
                                                               {:names
                                                                ["registry.k8s.io/etcd:3.5.21-0"
                                                                 "registry.k8s.io/etcd@sha256:d58c035df557080a27387d687092e3fc2b64c6d0e3162dc51453a115f847d121"],
                                                                :size_bytes 146133049}
                                                               {:names
                                                                ["registry.k8s.io/kube-apiserver:v1.33.1"
                                                                 "registry.k8s.io/kube-apiserver@sha256:d8ae2fb01c39aa1c7add84f3d54425cf081c24c11e3946830292a8cfa4293548"],
                                                                :size_bytes 97087431}
                                                               {:names
                                                                ["registry.k8s.io/kube-controller-manager:v1.33.1"
                                                                 "registry.k8s.io/kube-controller-manager@sha256:7c9bea694e3a3c01ed6a5ee02d55a6124cc08e0b2eec6caa33f2c396b8cbc3f8"],
                                                                :size_bytes 90534181}
                                                               {:names
                                                                ["registry.k8s.io/kube-proxy:v1.33.1"
                                                                 "registry.k8s.io/kube-proxy@sha256:7ddf379897139ae8ade8b33cb9373b70c632a4d5491da6e234f5d830e0a50807"],
                                                                :size_bytes 99683741}
                                                               {:names
                                                                ["registry.k8s.io/kube-scheduler:v1.33.1"
                                                                 "registry.k8s.io/kube-scheduler@sha256:395b7de7cdbdcc3c3a3db270844a3f71d757e2447a1e4db76b4cce46fba7fd55"],
                                                                :size_bytes 70545701}
                                                               {:names
                                                                ["registry.k8s.io/pause:3.10"
                                                                 "registry.k8s.io/pause@sha256:ee6521f290b2168b6e0935a181d4cff9be1ac3f505666ef0e3c98fae8199917a"],
                                                                :size_bytes 514000}
                                                               {:names
                                                                ["sixsq/nuvlaedge:2.18.0"
                                                                 "sixsq/nuvlaedge@sha256:82f41a6d2810811c529ade7030a6cdc65653055780f017a1fccc2ae0b280804a"],
                                                                :size_bytes 179704064}],
                                                :secrets      [{:type        "bootstrap.kubernetes.io/token",
                                                                :string_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels                        nil,
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "af11fe0d-a395-4354-ba98-1b43fd8b4c0e",
                                                                 :name                          "bootstrap-token-02zlb8",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T12:26:16+00:00",
                                                                 :namespace                     "kube-system",
                                                                 :resource_version              "254"},
                                                                :data
                                                                {:expiration                     "",
                                                                 :token-id                       "",
                                                                 :token-secret                   "",
                                                                 :auth-extra-groups              "",
                                                                 :usage-bootstrap-authentication "",
                                                                 :usage-bootstrap-signing        ""}}
                                                               {:type        "helm.sh/release.v1",
                                                                :string_data nil,
                                                                :kind        nil,
                                                                :immutable   nil,
                                                                :api_version nil,
                                                                :metadata
                                                                {:labels
                                                                 {:name       "nuvlabox-3ba7df6c-369f-4cbe-95a6-950d06218a3b",
                                                                  :status     "deployed",
                                                                  :modifiedAt "1751387252",
                                                                  :version    "1",
                                                                  :owner      "helm"},
                                                                 :generation                    nil,
                                                                 :deletion_timestamp            nil,
                                                                 :uid                           "7973af6d-10b3-4924-8df8-95685032a701",
                                                                 :name
                                                                 "sh.helm.release.v1.nuvlabox-3ba7df6c-369f-4cbe-95a6-950d06218a3b.v1",
                                                                 :deletion_grace_period_seconds nil,
                                                                 :finalizers                    nil,
                                                                 :owner_references              nil,
                                                                 :self_link                     nil,
                                                                 :annotations                   nil,
                                                                 :generate_name                 nil,
                                                                 :creation_timestamp            "2025-07-01T16:27:32+00:00",
                                                                 :namespace                     "default",
                                                                 :resource_version              "14376"},
                                                                :data        {:release ""}}]
                                                }},
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

(deftest get-deployment-coe-resources
  (is (= #{[:containers
            '({:Id     "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"
               :Image  "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker"
               :Labels {:com.docker.compose.project "b3b70820-2de4-4a11-b00c-a79661c3d433"
                        :com.docker.compose.service "agent"}
               :Ports  [{:IP          "0.0.0.0"
                         :PrivatePort 8000
                         :Type        "tcp"}
                        {:IP          "0.0.0.0"
                         :PrivatePort 80
                         :PublicPort  32786
                         :Type        "tcp"}
                        {:IP          "0.0.0.0"
                         :PrivatePort 8080
                         :PublicPort  33783
                         :Type        "udp"}]})]}
         (set (:docker (t/get-deployment-coe-resources
                         {:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                          :module {:compatibility "docker-compose"
                                   :subtype       "application"}}
                         nb-status-coe-docker-compose)))))
  (let [k8s-resources (:kubernetes (t/get-deployment-coe-resources
                                     {:id     "deployment/819b9a9e-010f-4c26-82d5-aa395bbb6179"
                                      :module {:subtype "application_kubernetes"}}
                                     nb-status-coe-kubernetes))]
    (is (= "Running" (-> k8s-resources :pods first :status :phase)))))


