(ns com.sixsq.slipstream.auth.utils.config-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.auth.utils.config :as cf]))

(deftest test-property-values

  (is (= "cd03c88b13517f931f09" (cf/property-value :github-client-id)))
  (is (nil? (cf/property-value :unknown)))

  (is (= "cd03c88b13517f931f09" (cf/mandatory-property-value :github-client-id)))
  (is (thrown? Exception (cf/mandatory-property-value :unknown))))
