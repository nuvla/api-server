(ns com.sixsq.slipstream.ssclj.resources.service-offer-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.service-offer :as so]))

(deftest test-valid-attribute-name
  (is (not (so/valid-attribute-name? #{"schema-org"} "a1")))
  (is (not (so/valid-attribute-name? #{"schema-org"} "schema-xxx:a1")))
  (is (not (so/valid-attribute-name? #{} "schema-xxx:a1")))
  (is (so/valid-attribute-name? #{"schema-org"} "schema-org:a1")))
