(ns com.sixsq.nuvla.server.resources.configuration-mitreid-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [com.sixsq.nuvla.server.resources.configuration-template-session-mitreid :as mitreid]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-mitreid
  (test-utils/check-lifecycle mitreid/service :client-id "server-assigned-client-id" "NEW_ID"))
