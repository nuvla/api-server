(ns sixsq.nuvla.db.es.binding-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [qbits.spandex :as spandex]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es.binding :as t]
    [sixsq.nuvla.db.es.common.es-mapping :as mapping]
    [sixsq.nuvla.db.es.common.utils :as escu]
    [sixsq.nuvla.db.es.utils :as esu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge-telemetry :as ts-nuvlaedge]
    [sixsq.nuvla.server.util.time :as time]))

(use-fixtures :each ltu/with-test-server-fixture)

(deftest check-es-protocol

  (let [client  (esu/create-es-client (ltu/es-test-endpoint (ltu/es-node)))
        binding (t/->ElasticsearchRestBinding client nil)]
    (lifecycle/check-binding-lifecycle binding))

  (with-open [client  (esu/create-es-client (ltu/es-test-endpoint (ltu/es-node)))
              binding (t/->ElasticsearchRestBinding client nil)]
    (queries/check-binding-queries binding))

  (testing "sniffer get closed when defined"
    (with-open [client   (esu/create-es-client (ltu/es-test-endpoint (ltu/es-node)))
                sniffer  (spandex/sniffer client)
                _binding (t/->ElasticsearchRestBinding client sniffer)])))

(deftest check-index-creation
  (with-open [client (esu/create-es-client (ltu/es-test-endpoint (ltu/es-node)))]
    (let [index-name "test-index-creation"]
      (t/create-index client index-name)
      (is (= {:number_of_shards   "3"
              :number_of_replicas "2"}
             (-> client
                 (spandex/request {:url index-name})
                 (get-in [:body (keyword index-name) :settings :index])
                 (select-keys [:number_of_shards :number_of_replicas])))))))

(deftest check-timeseries-index
  (with-open [client (esu/create-es-client (ltu/es-test-endpoint (ltu/es-node)))]
    (let [spec                  ::ts-nuvlaedge/schema
          mapping               (mapping/mapping spec {:dynamic-templates false, :fulltext false})
          routing-path          (mapping/time-series-routing-path ::ts-nuvlaedge/schema)
          index-name            "test-ts-index"
          template-name         (str index-name "-template")
          datastream-index-name "test-ts-index-1"]

      (testing "Create timeseries template"
        (t/create-timeseries-template client index-name mapping {:routing-path routing-path})
        (let [response (-> (spandex/request client {:url (str "_index_template/" template-name)})
                           (get-in [:body :index_templates 0]))]
          (is (= template-name (:name response)))
          (is (= {:index_patterns [(str index-name "*")]
                  :composed_of    []
                  :data_stream    {:hidden false, :allow_custom_routing false}}
                 (dissoc (:index_template response) :template)))
          (is (= {:index {:mode             "time_series"
                          :number_of_shards "3"
                          :routing_path     ["nuvlaedge-id"
                                             "metric"
                                             "disk.device"
                                             "network.interface"
                                             "power-consumption.metric-name"]}}
                 (get-in response [:index_template :template :settings])))))

      (testing "Create timeseries datastream"
        (t/create-datastream client datastream-index-name)
        (let [response (-> (spandex/request client {:url (str "_data_stream/" datastream-index-name)})
                           (get-in [:body :data_streams]))]
          (is (seq response)))))))

(deftest check-timeseries-ilm-policy
  (with-open [client (esu/create-es-client (ltu/es-test-endpoint (ltu/es-node)))]
    (let [spec          ::ts-nuvlaedge/schema
          mapping       (mapping/mapping spec {:dynamic-templates false, :fulltext false})
          routing-path  (mapping/time-series-routing-path ::ts-nuvlaedge/schema)
          collection-id "test-ilm-policy"
          index-name    (escu/collection-id->index collection-id)]
      (testing "Create ilm policy"
        (let [test-ilm-policy {:hot    {:min_age "0ms"
                                        :actions {:set_priority {:priority 100}}}
                               :warm   {:min_age "1d"
                                        :actions {:set_priority {:priority 50}
                                                  :downsample   {:fixed_interval "5m"}}}
                               :delete {:min_age "365d"
                                        :actions {:delete {:delete_searchable_snapshot true}}}}
              ilm-policy-name (t/create-or-update-lifecycle-policy client index-name test-ilm-policy)
              phases          (-> (spandex/request client {:url (str "_ilm/policy/" ilm-policy-name)})
                                  (get-in [:body (keyword ilm-policy-name) :policy :phases]))]
          (is (= #{:hot :warm :delete} (set (keys phases))))

          (testing "Create timeseries template with ilm policy"
            (let [template-name (t/create-timeseries-template client index-name mapping
                                                              {:routing-path   routing-path
                                                               :start-time     (time/to-str (time/minus (time/now) (time/duration-unit 20 :hours)))
                                                               :lifecycle-name ilm-policy-name})
                  response      (-> (spandex/request client {:url (str "_index_template/" template-name)})
                                    (get-in [:body :index_templates 0]))]
              (is (= template-name (:name response)))
              (is (= ilm-policy-name (get-in response [:index_template :template :settings :index :lifecycle :name])))))

          (testing "Test datastream with above ilm policy"
            (t/create-datastream client index-name)
            (let [_response          (-> (spandex/request client {:url (str "_data_stream/" index-name)})
                                         (get-in [:body :data_streams]))
                  start-time         (time/minus (time/now) (time/duration-unit 1 :seconds))
                  test-data-last-sec (map (fn [i] {:timestamp (-> start-time
                                                                  (time/plus (time/duration-unit (* i 10) :millis))
                                                                  time/to-str)
                                                   :metric    "ram"
                                                   :ram       {:used 0}})
                                          (range 100))]
              (t/bulk-insert-metrics client collection-id test-data-last-sec {}))
            (spandex/request client {:url [:_refresh], :method :post})
            (let [_response (-> (spandex/request client {:url (str "_data_stream/" index-name)})
                                (get-in [:body :data_streams]))
                  response  (-> (spandex/request client {:url (str index-name "/_search")})
                                :body)]
              (spandex/request client {:url [:_refresh], :method :post})
              (is (= 100 (get-in response [:hits :total :value])))))

          (testing "Update ilm policy"
            (let [test-ilm-policy {:hot    {:min_age "0ms"
                                            :actions {:set_priority {:priority 100}}}
                                   :warm   {:min_age "1d"
                                            :actions {:set_priority {:priority 50}
                                                      :downsample   {:fixed_interval "5m"}}}
                                   :cold   {:min_age "30d"
                                            :actions {:set_priority {:priority 30}
                                                      :downsample   {:fixed_interval "1h"}}}
                                   :delete {:min_age "365d"
                                            :actions {:delete {:delete_searchable_snapshot true}}}}
                  ilm-policy-name (t/create-or-update-lifecycle-policy client index-name test-ilm-policy)
                  phases          (-> (spandex/request client {:url (str "_ilm/policy/" ilm-policy-name)})
                                      (get-in [:body (keyword ilm-policy-name) :policy :phases]))]
              (is (= #{:hot :warm :cold :delete} (set (keys phases)))))))))))

