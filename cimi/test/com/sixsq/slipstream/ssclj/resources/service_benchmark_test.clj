(ns com.sixsq.slipstream.ssclj.resources.service-benchmark-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.service-catalog.utils :as sc]))

(deftest test-valid-attribute-name
  (is (not (sc/valid-attribute-name? #{"schema-org"} "a1")))
  (is (not (sc/valid-attribute-name? #{"schema-org"} "schema-xxx:a1")))
  (is (not (sc/valid-attribute-name? #{} "schema-xxx:a1")))
  (is (sc/valid-attribute-name? #{"schema-org"} "schema-org:a1")))
