(ns com.sixsq.nuvla.server.resources.data-record-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.data-record :as t]))


(deftest test-valid-attribute-name
  (is (t/valid-key-prefix? #{"schema-org"} "a1"))
  (is (not (t/valid-key-prefix? #{"schema-org"} "schema-xxx:a1")))
  (is (not (t/valid-key-prefix? #{} "schema-xxx:a1")))
  (is (t/valid-key-prefix? #{"schema-org"} "schema-org:a1")))
