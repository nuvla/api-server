(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-vpn-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-vpn :as tpl-vpn]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-vpn :as spec-vpn]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-service-template-create-swarm-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                         (str tpl/resource-type "/vpn")
                   :resource-type              tpl/resource-type
                   :created                    timestamp
                   :updated                    timestamp
                   :acl                        valid-acl

                   :method                     tpl-vpn/method
                   :subtype                    tpl-vpn/subtype

                   :vpn-ca-certificate     "ca certif"
                   :vpn-common-name-prefix "common name prefix"
                   :vpn-scope              "nuvlabox"
                   :vpn-endpoints          [{:protocol "tcp"
                                                 :port     1194
                                                 :endpoint "10.0.0.1"}
                                                {:protocol "udp"
                                                 :port     1194
                                                 :endpoint "10.0.0.1"}]
                   :vpn-intermediate-ca    ["certif-1"
                                                "certif-2"]}]

    (stu/is-valid ::spec-vpn/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method
                   :vpn-ca-certificate :vpn-endpoints :vpn-scope}]
      (stu/is-invalid ::spec-vpn/schema (dissoc cfg attr)))

    (doseq [attr #{:vpn-common-name-prefix :vpn-intermediate-ca}]
      (stu/is-valid ::spec-vpn/schema (dissoc cfg attr)))))
