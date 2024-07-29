(ns com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service-vpn-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.credential :as cred]
    [com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service-vpn
     :as cred-infra-service-vpn]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl       {:id                        (str cred/resource-type "/uuid")
                   :resource-type             cred/resource-type
                   :created                   timestamp
                   :updated                   timestamp
                   :acl                       valid-acl

                   :subtype                   "vpn"
                   :method                    "vpn"

                   :parent                    "infrastructure-service/service-1"

                   :vpn-common-name       "common-name-example"
                   :vpn-certificate       "client certificate example"
                   :vpn-certificate-owner "user/jane"
                   :vpn-intermediate-ca   ["certif-1"
                                               "certif-2"]}]

    (stu/is-valid ::cred-infra-service-vpn/schema tpl)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :subtype :method :parent
                :vpn-common-name :vpn-certificate}]
      (stu/is-invalid ::cred-infra-service-vpn/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{:vpn-intermediate-ca}]
      (stu/is-valid ::cred-infra-service-vpn/schema (dissoc tpl k)))))
