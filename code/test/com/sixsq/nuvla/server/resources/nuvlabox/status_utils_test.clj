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
