(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-openvpn-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-openvpn
     :as cred-infra-service-openvpn]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl       {:id                      (str cred/resource-type "/uuid")
                   :resource-type           cred/resource-type
                   :created                 timestamp
                   :updated                 timestamp
                   :acl                     valid-acl

                   :subtype                 "openvpn"
                   :method                  "openvpn"

                   :parent                  "infrastructure-service/service-1"

                   :openvpn-common-name     "common-name-example"
                   :openvpn-certificate     "client certificate example"
                   :openvpn-intermediate-ca ["certif-1"
                                             "certif-2"]}]

    (stu/is-valid ::cred-infra-service-openvpn/schema tpl)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :subtype :method :parent
                :openvpn-common-name :openvpn-certificate}]
      (stu/is-invalid ::cred-infra-service-openvpn/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{:openvpn-intermediate-ca}]
      (stu/is-valid ::cred-infra-service-openvpn/schema (dissoc tpl k)))))
