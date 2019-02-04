(ns com.sixsq.slipstream.db.es-rest.binding-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.db.binding-lifecycle :as lifecycle]
    [com.sixsq.slipstream.db.binding-queries :as queries]
    [com.sixsq.slipstream.db.es-rest.binding :as t]
    [com.sixsq.slipstream.db.es.es-node :as es-node]))


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
