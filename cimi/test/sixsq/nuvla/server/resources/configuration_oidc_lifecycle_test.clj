(ns sixsq.nuvla.server.resources.configuration-oidc-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-session-oidc :as oidc]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-oidc
  (test-utils/check-lifecycle oidc/service :clientID "server-assigned-client-id" "NEW_ID"))
