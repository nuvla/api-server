(ns sixsq.nuvla.server.resources.data-record-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.data-record :as data-record]))

(deftest test-valid-attribute-name
  (is (data-record/valid-key-prefix? #{"schema-org"} "a1"))
  (is (not (data-record/valid-key-prefix? #{"schema-org"} "schema-xxx:a1")))
  (is (not (data-record/valid-key-prefix? #{} "schema-xxx:a1")))
  (is (data-record/valid-key-prefix? #{"schema-org"} "schema-org:a1")))
