(ns com.sixsq.slipstream.ssclj.resources.configuration-mitreid-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.slipstream.ssclj.resources.configuration-lifecycle-test-utils :as test-utils]
    [com.sixsq.slipstream.ssclj.resources.configuration-template-session-mitreid :as mitreid]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-mitreid
  (test-utils/check-lifecycle mitreid/service :clientID "server-assigned-client-id" "NEW_ID"))
