(ns sixsq.nuvla.server.resources.configuration-github-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-session-github :as github]
    [sixsq.nuvla.server.resources.configuration-template-session-oidc :as oidc]
    [sixsq.nuvla.server.resources.configuration-template-slipstream :as slipstream]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-github
  (test-utils/check-lifecycle github/service :clientID "github-oauth-application-client-id" "NEW_ID"))
