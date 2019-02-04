(ns com.sixsq.slipstream.db.es.filter-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.slipstream.db.es.filter :as t]))

(deftest test-attribute-conversion
  (is (= [:Attribute "att"] (t/convert [:Attribute "att"])))
  (is (= [:Attribute "ns1:att1"] (t/convert [:Attribute "ns1:att1"])))
  (is (= [:Attribute "ns1:att1.ns2:att2"] (t/convert [:Attribute "ns1:att1/ns2:att2"]))))
