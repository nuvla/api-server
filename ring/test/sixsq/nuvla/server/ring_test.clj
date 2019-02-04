(ns sixsq.nuvla.server.ring-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.ring :as t])
  (:import
    (clojure.lang ExceptionInfo)))


(deftest check-as-symbol
  (let [as-symbol @#'t/as-symbol]
    (are [expected input] (= expected (as-symbol input))
                          'environ.core/env "environ.core/env"
                          'environ.core/env 'environ.core/env
                          'environ.core/env :environ.core/env)
    (is (thrown-with-msg? ExceptionInfo #"invalid symbol" (as-symbol 10)))
    (is (thrown-with-msg? ExceptionInfo #"invalid symbol" (as-symbol nil)))))


(deftest check-ns-and-var
  (let [ns-and-var @#'t/ns-and-var]
    (are [expected input] (= expected (ns-and-var input))
                          '[environ.core env] 'environ.core/env)
    (is (thrown-with-msg? ExceptionInfo #"complete, namespaced" (ns-and-var 'no-namespace)))))


(deftest check-resolve-var
  (let [resolve-var @#'t/resolve-var]
    (are [input] (apply resolve-var input)
                 '[environ.core env]
                 '[aleph.http start-server])
    (is (nil? (apply resolve-var '[environ.core unknown])))
    (is (thrown-with-msg? ExceptionInfo #"could not resolve" (apply resolve-var '[unknown unknown])))))


(deftest check-dyn-resolve
  (let [dyn-resolve @#'t/dyn-resolve]
    (are [input] (dyn-resolve input)
                 'environ.core/env
                 "environ.core/env"
                 'aleph.http/start-server
                 "aleph.http/start-server")
    (is (thrown-with-msg? ExceptionInfo #"complete, namespaced" (dyn-resolve 'invalid.symbol)))
    (is (thrown-with-msg? ExceptionInfo #"error requiring namespace" (dyn-resolve 'unknown/var)))
    (is (thrown-with-msg? ExceptionInfo #"error requiring namespace" (dyn-resolve "unknown/var")))))


(deftest check-validate-host
  (let [validate-host @#'t/validate-host]
    (are [expected input] (= expected (validate-host input))
                          t/default-host nil
                          t/default-host 10.2
                          t/default-host 65537
                          t/default-host -1
                          "10" "10"
                          "0.0.0.0" "0.0.0.0")))


(deftest check-parse-port
  (let [parse-port @#'t/parse-port]
    (are [expected input] (= expected (parse-port input))
                          t/default-port nil
                          t/default-port 10.2
                          t/default-port "invalid"
                          t/default-port 65537
                          t/default-port -1
                          10 10
                          10 "10")))

