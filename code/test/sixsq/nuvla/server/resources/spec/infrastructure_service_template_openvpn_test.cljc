(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-openvpn-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-openvpn :as tpl-openvpn]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-openvpn :as spec-openvpn]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-service-template-create-swarm-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                         (str tpl/resource-type "/openvpn")
                   :resource-type              tpl/resource-type
                   :created                    timestamp
                   :updated                    timestamp
                   :acl                        valid-acl

                   :method                     tpl-openvpn/method
                   :subtype                    tpl-openvpn/subtype

                   :openvpn-ca-certificate     "ca certif"
                   :openvpn-common-name-prefix "common name prefix"
                   :openvpn-scope              "nuvlabox"
                   :openvpn-endpoints          [{:protocol "tcp"
                                                 :port     1194
                                                 :endpoint "10.0.0.1"}
                                                {:protocol "udp"
                                                 :port     1194
                                                 :endpoint "10.0.0.1"}]
                   :openvpn-intermediate-ca    ["certif-1"
                                                "certif-2"]}]

    (stu/is-valid ::spec-openvpn/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method
                   :openvpn-ca-certificate :openvpn-endpoints :openvpn-scope}]
      (stu/is-invalid ::spec-openvpn/schema (dissoc cfg attr)))

    (doseq [attr #{:openvpn-common-name-prefix :openvpn-intermediate-ca}]
      (stu/is-valid ::spec-openvpn/schema (dissoc cfg attr)))))
