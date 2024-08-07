(ns com.sixsq.nuvla.server.resources.configuration-oidc-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [com.sixsq.nuvla.server.resources.configuration-template-session-oidc :as oidc]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-oidc
  (test-utils/check-lifecycle oidc/service :client-id "server-assigned-client-id" "NEW_ID"))
