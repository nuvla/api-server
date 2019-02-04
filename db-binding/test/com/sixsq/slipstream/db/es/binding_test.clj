(ns com.sixsq.slipstream.db.es.binding-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.slipstream.db.binding-lifecycle :as lifecycle]
    [com.sixsq.slipstream.db.binding-queries :as queries]
    [com.sixsq.slipstream.db.es.binding :as t]
    [com.sixsq.slipstream.db.es.es-node :as es-node]
    [com.sixsq.slipstream.db.es.utils :as esu]))


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
