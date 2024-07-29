(ns com.sixsq.nuvla.server.resources.credential-infrastructure-service-vpn-nuvlabox-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-customer
     :as ctisoc]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-nuvlabox
     :as ctison]
    [com.sixsq.nuvla.server.resources.credential.vpn-utils-test :as vpn-utils-test]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ctison/resource-url)))


(deftest lifecycle
  (vpn-utils-test/credential-vpn-lifecycle-test
    ctison/method "nuvlabox" "nuvlabox/bernard"
    "nuvlabox/bernard nuvlabox/bernard group/nuvla-nuvlabox group/nuvla-user group/nuvla-anon" ctisoc/method))
