(ns sixsq.nuvla.server.resources.credential-infrastructure-service-vpn-customer-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
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
  (mdtu/check-metadata-exists (str credential/resource-type "-" ctisoc/resource-url)))


(deftest lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (vpn-utils-test/credential-vpn-lifecycle-test
     ctisoc/method "customer" "user/jane" "user/jane user/jane group/nuvla-user group/nuvla-anon"
     ctison/method)))
