(ns com.sixsq.slipstream.ssclj.resources.configuration-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.slipstream.ssclj.resources.configuration-lifecycle-test-utils :as test-utils]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)

;; see separate test namespaces for each configuration type

(deftest bad-methods
  (test-utils/check-bad-methods))
