(ns sixsq.nuvla.db.es.binding-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [sixsq.nuvla.db.binding-queries :as queries]
    [sixsq.nuvla.db.es.binding :as t]
    [sixsq.nuvla.db.es.es-node :as es-node]
    [sixsq.nuvla.db.es.utils :as esu]))


(deftest check-es-native-protocol
  (with-open [test-node (es-node/create-test-node)
              binding (-> test-node
                          (.client)
                          esu/wait-for-cluster
                          t/->ESBindingLocal)]
    (lifecycle/check-binding-lifecycle binding))

  (with-open [test-node (es-node/create-test-node)
              binding (-> test-node
                          (.client)
                          esu/wait-for-cluster
                          t/->ESBindingLocal)]
    (queries/check-binding-queries binding)))
