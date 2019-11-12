(ns sixsq.nuvla.server.resources.configuration-openvpn-api-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template-openvpn-api :as openvpn-api]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :each ltu/with-test-server-fixture)


(deftest lifecycle-openvpn-api
  (test-utils/check-lifecycle openvpn-api/service
                              :endpoint "http://openvpn.sixsq.example" "http://other.url.example"))
