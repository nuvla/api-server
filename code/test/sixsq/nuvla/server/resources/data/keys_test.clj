(ns sixsq.nuvla.server.resources.data.keys-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.data.keys :as key-utils]))


(deftest test-valid-attribute-name
  (is (not (key-utils/valid-attribute-name? #{"schema-org"} "a1")))
  (is (not (key-utils/valid-attribute-name? #{"schema-org"} "schema-xxx:a1")))
  (is (not (key-utils/valid-attribute-name? #{} "schema-xxx:a1")))
  (is (key-utils/valid-attribute-name? #{"schema-org"} "schema-org:a1")))
