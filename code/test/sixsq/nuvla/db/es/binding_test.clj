(ns sixsq.nuvla.db.es.binding-test
  (:require
    [clojure.test :refer [deftest is use-fixtures testing]]
    [qbits.spandex :as spandex]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es.binding :as t]
    [sixsq.nuvla.db.es.common.es-mapping :as mapping]
    [sixsq.nuvla.db.es.utils :as esu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge :as ts-nuvlaedge]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-es-protocol

  (let [client  (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})
        binding (t/->ElasticsearchRestBinding client nil)]
    (lifecycle/check-binding-lifecycle binding))

  (with-open [client  (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})
              binding (t/->ElasticsearchRestBinding client nil)]
    (queries/check-binding-queries binding))

  (testing "sniffer get closed when defined"
    (with-open [client   (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})
                sniffer  (spandex/sniffer client)
                _binding (t/->ElasticsearchRestBinding client sniffer)])))

(deftest check-index-creation
  (with-open [client (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})]
    (let [index-name "test-index-creation"]
      (t/create-index client index-name)
      (is (= {:number_of_shards   "3"
              :number_of_replicas "2"}
             (-> client
                 (spandex/request {:url index-name})
                 (get-in [:body (keyword index-name) :settings :index])
                 (select-keys [:number_of_shards :number_of_replicas])))))))

(deftest check-timeseries-index
  (with-open [client (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})]
    (let [spec                  ::ts-nuvlaedge/schema
          mapping               (mapping/mapping spec {:dynamic-templates false, :fulltext false})
          routing-path          (mapping/time-series-routing-path ::ts-nuvlaedge/schema)
          index-name            "test-ts-index"
          template-name         (str index-name "-template")
          datastream-index-name "test-ts-index-1"]

      (testing "Create timeseries template"
        (t/create-timeseries-template client index-name mapping routing-path)
        (let [response (-> (spandex/request client {:url (str "_index_template/" template-name)})
                           (get-in [:body :index_templates 0]))]
          (is (= template-name (:name response)))
          (is (= {:index_patterns [(str index-name "*")]
                  :composed_of    []
                  :data_stream    {:hidden false, :allow_custom_routing false}}
                 (dissoc (:index_template response) :template)))
          (is (= {:index {:mode         "time_series"
                          :routing_path ["nuvlaedge-id"
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


