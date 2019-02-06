(ns sixsq.nuvla.db.es-rest.binding-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es-rest.binding :as t]
    [sixsq.nuvla.db.es.es-node :as es-node]))


(deftest check-es-rest-protocol

  (with-open [test-node (es-node/create-test-node)
              binding (-> {:hosts ["localhost:9200"]}
                          t/create-client
                          t/->ElasticsearchRestBinding)]
    (lifecycle/check-binding-lifecycle binding))


  (with-open [test-node (es-node/create-test-node)
              binding (-> {:hosts ["localhost:9200"]}
                          t/create-client
                          t/->ElasticsearchRestBinding)]
    (queries/check-binding-queries binding)))
