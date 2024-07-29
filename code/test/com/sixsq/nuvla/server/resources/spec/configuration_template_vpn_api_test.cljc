(ns com.sixsq.nuvla.server.resources.spec.configuration-template-vpn-api-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-vpn-api :as cts-vpn-api]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id                      (str ct/resource-type "/vpn-api-test-instance")
                   :resource-type           ct/resource-type
                   :created                 timestamp
                   :updated                 timestamp
                   :acl                     valid-acl

                   :service                 "vpn-api"
                   :instance                "test-instance"

                   :endpoint                "http://vpn.api"

                   :infrastructure-services ["infrastructure-service/vpn-1"
                                             "infrastructure-service/vpn-2"]}]

    (stu/is-valid ::cts-vpn-api/schema root)

    (stu/is-invalid ::cts-vpn-api/schema (assoc root :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated :acl
                :service :instance :endpoint :infrastructure-services}]
      (stu/is-invalid ::cts-vpn-api/schema (dissoc root k)))))
