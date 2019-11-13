(ns sixsq.nuvla.db.es.binding-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es.binding :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-es-protocol

  (with-open [client (t/create-client {:hosts ["localhost:9200"]})
              sniffer (t/create-sniffer client {})
              binding (t/->ElasticsearchRestBinding client sniffer)]
    (lifecycle/check-binding-lifecycle binding))

  (with-open [client (t/create-client {:hosts ["localhost:9200"]})
              sniffer (t/create-sniffer client {})
              binding (t/->ElasticsearchRestBinding client sniffer)]
    (queries/check-binding-queries binding)))
