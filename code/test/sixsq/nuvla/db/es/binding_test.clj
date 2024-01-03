(ns sixsq.nuvla.db.es.binding-test
  (:require
    [clojure.test :refer [deftest is use-fixtures testing]]
    [qbits.spandex :as spandex]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es.binding :as t]
    [sixsq.nuvla.db.es.utils :as esu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-es-protocol

  (let [client  (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})
        binding (t/->ElasticsearchRestBinding client nil)]
    (lifecycle/check-binding-lifecycle binding))

  (with-open [client  (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})
              binding (t/->ElasticsearchRestBinding client nil)]
    (queries/check-binding-queries binding))

  (testing "sniffer get closed when defined"
    (with-open [client  (esu/create-client {:hosts (ltu/es-test-endpoint (ltu/es-node))})
                sniffer (spandex/sniffer client)]
      (t/->ElasticsearchRestBinding client sniffer))))

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
