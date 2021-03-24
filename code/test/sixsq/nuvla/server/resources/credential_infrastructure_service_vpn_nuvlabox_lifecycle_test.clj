(ns sixsq.nuvla.server.resources.credential-infrastructure-service-vpn-nuvlabox-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-customer
     :as ctisoc]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-nuvlabox
     :as ctison]
    [sixsq.nuvla.server.resources.credential.vpn-utils-test :as vpn-utils-test]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ctison/resource-url)))


(deftest lifecycle
  (vpn-utils-test/credential-vpn-lifecycle-test
    ctison/method "nuvlabox" "nuvlabox/bernard"
    "nuvlabox/bernard nuvlabox/bernard group/nuvla-nuvlabox group/nuvla-user group/nuvla-anon" ctisoc/method))
