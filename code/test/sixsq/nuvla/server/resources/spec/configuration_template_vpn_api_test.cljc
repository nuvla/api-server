(ns sixsq.nuvla.server.resources.spec.configuration-template-openvpn-api-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.spec.configuration-template-openvpn-api :as cts-openvpn-api]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id                      (str ct/resource-type "/openvpn-api-test-instance")
                   :resource-type           ct/resource-type
                   :created                 timestamp
                   :updated                 timestamp
                   :acl                     valid-acl

                   :service                 "openvpn-api"
                   :instance                "test-instance"

                   :endpoint                "http://openvpn.api"

                   :infrastructure-services ["infrastructure-service/openvpn-1"
                                             "infrastructure-service/openvpn-2"]}]

    (stu/is-valid ::cts-openvpn-api/schema root)

    (stu/is-invalid ::cts-openvpn-api/schema (assoc root :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated :acl
                :service :instance :endpoint :infrastructure-services}]
      (stu/is-invalid ::cts-openvpn-api/schema (dissoc root k)))))
