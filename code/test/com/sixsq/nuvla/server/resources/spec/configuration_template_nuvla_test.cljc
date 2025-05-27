(ns com.sixsq.nuvla.server.resources.spec.configuration-template-nuvla-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as nuvla]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as ct-nuvla]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-admin"]})


(deftest check-configuration-template-nuvla
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                          (str ct/resource-type "/" nuvla/service)
                   :resource-type               nuvla/service
                   :created                     timestamp
                   :updated                     timestamp
                   :acl                         valid-acl

                   :service                     nuvla/service

                   :smtp-username               "username"
                   :smtp-password               "password"
                   :smtp-host                   "host"
                   :smtp-port                   465
                   :smtp-ssl                    true
                   :smtp-debug                  true
                   :smtp-xoauth2                "google"
                   :smtp-xoauth2-config         {}

                   :support-email               "admin@example.org"

                   :stripe-api-key              "sk_test_xxx"
                   :external-vulnerabilities-db "https://github.com/nuvla/vuln-db/blob/main/databases/all.aggregated.json.gz?raw=true"

                   :conditions-url              "https://nuvla.io/terms/tos"
                   :email-header-img-url        "https://nuvla.io/ui/images/email-header.png"
                   :authorized-redirect-urls    ["https://nuvla.io"]}]

    (stu/is-valid ::ct-nuvla/schema cfg)

    ;; mandatory keys
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::ct-nuvla/schema (dissoc cfg k)))

    ;; optional keys
    (doseq [k #{:smtp-username :smtp-password :smtp-host :smtp-port :smtp-ssl :smtp-debug
                :support-email :stripe-api-key :external-vulnerabilities-db :conditions-url
                :email-header-img-url :authorized-redirect-urls :smtp-xoauth2}]
      (stu/is-valid ::ct-nuvla/schema (dissoc cfg k)))))
