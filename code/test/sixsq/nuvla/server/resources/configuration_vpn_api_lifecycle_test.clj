(ns sixsq.nuvla.server.resources.configuration-vpn-api-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-vpn-api :as vpn-api]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-vpn-api
  (test-utils/check-lifecycle vpn-api/service
                              :endpoint "http://vpn.sixsq.example" "http://other.url.example"))
