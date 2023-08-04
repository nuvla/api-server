(ns sixsq.nuvla.server.resources.common.dynamic-load-test
  (:require [clojure.test :refer [deftest testing is]]
            [sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
            [sixsq.nuvla.server.util.namespace-utils :as ns-util]))

(deftest initialization-order
  (let [init-seq (atom [])]
    (with-redefs-fn
      (->> (dyn/resource-namespaces)
           (keep (fn [resource-ns]
                   (when-let [init-fn (resolve (symbol (str resource-ns) "initialize"))]
                     [init-fn
                      (fn []
                        (swap! init-seq conj
                               [(ns-name resource-ns)
                                (ns-util/initialization-order resource-ns)]))])))
           (into {}))
      (fn []
        (dyn/initialize)
        (testing "Initialization order is respected"
          (is (apply <= (map second @init-seq))))
        (testing "Event resource initialized first"
          (is (= (symbol "sixsq.nuvla.server.resources.event")
                 (ffirst @init-seq))))))))
