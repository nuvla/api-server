(ns sixsq.nuvla.server.resources.service-catalog.utils-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.service-catalog.utils :as sc]))


(deftest test-valid-attribute-name
  (is (not (sc/valid-attribute-name? #{"schema-org"} "a1")))
  (is (not (sc/valid-attribute-name? #{"schema-org"} "schema-xxx:a1")))
  (is (not (sc/valid-attribute-name? #{} "schema-xxx:a1")))
  (is (sc/valid-attribute-name? #{"schema-org"} "schema-org:a1")))
