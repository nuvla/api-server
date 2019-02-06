(ns sixsq.nuvla.server.resources.configuration-mitreid-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-session-mitreid :as mitreid]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-mitreid
  (test-utils/check-lifecycle mitreid/service :clientID "server-assigned-client-id" "NEW_ID"))
