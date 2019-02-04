(ns com.sixsq.slipstream.ssclj.resources.configuration-github-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.slipstream.ssclj.resources.configuration-lifecycle-test-utils :as test-utils]
    [com.sixsq.slipstream.ssclj.resources.configuration-template-session-github :as github]
    [com.sixsq.slipstream.ssclj.resources.configuration-template-session-oidc :as oidc]
    [com.sixsq.slipstream.ssclj.resources.configuration-template-slipstream :as slipstream]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-github
  (test-utils/check-lifecycle github/service :clientID "github-oauth-application-client-id" "NEW_ID"))
