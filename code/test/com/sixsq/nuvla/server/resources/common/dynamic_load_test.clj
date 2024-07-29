(ns com.sixsq.nuvla.server.resources.common.dynamic-load-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
            [com.sixsq.nuvla.server.util.namespace-utils :as ns-util]))

(defn define-random-initilization-order [resource-ns]
  (doto resource-ns
    (intern 'initialization-order
            (rand-int 10000))))


(defn undefine-initilization-order [resource-ns]
  (ns-unmap resource-ns 'initialization-order))


(deftest initialization-order
  (let [init-seq (atom nil)]
    ;; redefine the `initialize` fn of resource namespaces to only touch the local atom
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
        (let [init-resources (fn []
                               (reset! init-seq [])
                               (dyn/initialize))]
          (testing "Configuration template nuvla resource initialized first"
            (init-resources)
            (is (= 'com.sixsq.nuvla.server.resources.configuration-template-nuvla
                   (ffirst @init-seq))))
          (testing "Initialization order is respected in general"
            ;; set random initialization order on every ns that does not define it already
            (let [ns-list (doall (for [resource-ns (dyn/resource-namespaces)
                                       :when (not (ns-util/resolve "initialization-order" resource-ns))]
                                   (define-random-initilization-order resource-ns)))]
              (try
                ;; run initialization
                (init-resources)
                (finally
                  ;; undefine initialization order wherever it was set randomly
                  (doseq [resource-ns ns-list]
                    (undefine-initilization-order resource-ns)))))
            ;; check that initialization happened in the right order
            (is (apply <= (map second @init-seq)))))))))

