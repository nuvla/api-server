(ns sixsq.nuvla.server.resources.configuration-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)

;; see separate test namespaces for each configuration type

(deftest bad-methods
  (test-utils/check-bad-methods))
