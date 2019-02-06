(ns sixsq.nuvla.server.resources.configuration-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-session-github :as github]
    [sixsq.nuvla.server.resources.configuration-template-session-mitreid :as mitreid]
    [sixsq.nuvla.server.resources.configuration-template-session-mitreid-token :as mitreid-token]
    [sixsq.nuvla.server.resources.configuration-template-session-oidc :as oidc]
    [sixsq.nuvla.server.resources.configuration-template-slipstream :as slipstream]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context (u/de-camelcase ct/resource-name)))


(deftest retrieve-by-id
  (test-utils/check-retrieve-by-id slipstream/service)
  (test-utils/check-retrieve-by-id oidc/service)
  (test-utils/check-retrieve-by-id mitreid/service)
  (test-utils/check-retrieve-by-id mitreid-token/service)
  (test-utils/check-retrieve-by-id github/service))

(deftest lifecycle
  (test-utils/check-lifecycle slipstream/service)
  (test-utils/check-lifecycle oidc/service)
  (test-utils/check-lifecycle mitreid/service)
  (test-utils/check-lifecycle mitreid-token/service)
  (test-utils/check-lifecycle github/service))

(deftest bad-methods
  (test-utils/check-bad-methods))
