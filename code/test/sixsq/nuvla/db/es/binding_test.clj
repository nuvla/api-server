(ns sixsq.nuvla.db.es.binding-test
  (:require
    [clojure.test :refer [are deftest is use-fixtures]]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es.binding :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-es-protocol

  (with-open [binding (-> {:hosts ["localhost:9200"]}
                          t/create-client
                          t/->ElasticsearchRestBinding)]
    (lifecycle/check-binding-lifecycle binding))


  (with-open [binding (-> {:hosts ["localhost:9200"]}
                          t/create-client
                          t/->ElasticsearchRestBinding)]
    (queries/check-binding-queries binding)))
