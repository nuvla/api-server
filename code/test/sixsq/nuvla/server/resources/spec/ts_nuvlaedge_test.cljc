(ns sixsq.nuvla.server.resources.spec.ts-nuvlaedge-test
  (:require
    [clojure.test :refer [is deftest]]
    [sixsq.nuvla.db.es.common.es-mapping :as es-mapping]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge :as ts-nuvlaedge]))

(deftest check-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"]
    (doseq [valid-entry [{:nuvlaedge-id "nuvlabox/1"
                          :metric       "cpu"
                          :cpu          {:capacity            8
                                         :load                4.5
                                         :load-1              4.3
                                         :load-5              5.5
                                         :system-calls        0
                                         :interrupts          13621648
                                         :software-interrupts 37244
                                         :context-switches    382731}
                          :timestamp    timestamp}
                         {:nuvlaedge-id "nuvlabox/1"
                          :metric       "ram"
                          :ram          {:capacity 4096
                                         :used     1000}
                          :timestamp    timestamp}
                         {:nuvlaedge-id "nuvlabox/1"
                          :metric       "disk"
                          :disk         {:device   "root"
                                         :capacity 20000
                                         :used     10000}
                          :timestamp    timestamp}
                         {:nuvlaedge-id "nuvlabox/1"
                          :metric       "network"
                          :network      {:interface         "eth0"
                                         :bytes-received    5247943
                                         :bytes-transmitted 41213}
                          :timestamp    timestamp}
                         {:nuvlaedge-id      "nuvlabox/1"
                          :metric            "power-consumption"
                          :power-consumption {:metric-name        "IN_current"
                                              :energy-consumption 2.4
                                              :unit               "A"}
                          :timestamp         timestamp}]]

      (stu/is-valid ::ts-nuvlaedge/schema valid-entry)
      (stu/is-invalid ::ts-nuvlaedge/schema (assoc valid-entry :unknown "value"))

      (doseq [attr #{:nuvlaedge-id :timestamp}]
        (stu/is-invalid ::ts-nuvlaedge/schema (dissoc valid-entry attr)))

      (doseq [attr #{:cpu}]
        (stu/is-valid ::ts-nuvlaedge/schema (dissoc valid-entry attr))))))

(deftest check-transform-mapping
  (is (= (es-mapping/mapping ::ts-nuvlaedge/schema {:dynamic-templates false
                                                    :fulltext          false})
         {:properties {"nuvlaedge-id"      {:type                  "keyword"
                                            :time_series_dimension true}
                       "metric"            {:type                  "keyword"
                                            :time_series_dimension true}
                       "@timestamp"        {:type "date", :format "strict_date_optional_time||epoch_millis"}
                       "cpu"               {:type "object", :properties {"capacity"            {:type               "long"
                                                                                                :time_series_metric "gauge"}
                                                                         "load"                {:type               "double"
                                                                                                :time_series_metric "gauge"}
                                                                         "load-1"              {:type               "double"
                                                                                                :time_series_metric "gauge"}
                                                                         "load-5"              {:type               "double"
                                                                                                :time_series_metric "gauge"}
                                                                         "context-switches"    {:type               "double"
                                                                                                :time_series_metric "counter"}
                                                                         "interrupts"          {:type               "double"
                                                                                                :time_series_metric "counter"}
                                                                         "software-interrupts" {:type               "double"
                                                                                                :time_series_metric "counter"}
                                                                         "system-calls"        {:type               "double"
                                                                                                :time_series_metric "counter"}}}
                       "ram"               {:type "object", :properties {"capacity" {:type               "long"
                                                                                     :time_series_metric "gauge"}
                                                                         "used"     {:type "long"
                                                                                     :time_series_metric "gauge"}}}
                       "disk"              {:type "object", :properties {"device"   {:type                  "keyword"
                                                                                     :time_series_dimension true}
                                                                         "capacity" {:type               "long"
                                                                                     :time_series_metric "gauge"}
                                                                         "used"     {:type "long"
                                                                                     :time_series_metric "gauge"}}}
                       "network"           {:type "object", :properties {"interface"         {:type                  "keyword"
                                                                                              :time_series_dimension true}
                                                                         "bytes-received"    {:type "double"
                                                                                              :time_series_metric "counter"}
                                                                         "bytes-transmitted" {:type "double"
                                                                                              :time_series_metric "counter"}}}
                       "power-consumption" {:type "object", :properties {"metric-name"        {:type                  "keyword"
                                                                                               :time_series_dimension true}
                                                                         "energy-consumption" {:type "double"
                                                                                               :time_series_metric "gauge"}
                                                                         "unit"               {:type "keyword"}}}}})))

(deftest check-routing-path
  (is (= ["nuvlaedge-id"
          "metric"
          "disk.device"
          "network.interface"
          "power-consumption.metric-name"]
         (es-mapping/time-series-routing-path ::ts-nuvlaedge/schema))))

