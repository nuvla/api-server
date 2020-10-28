(ns sixsq.nuvla.server.resources.spec.configuration-template-nuvla-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.configuration-nuvla :as nuvla]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as ct-nuvla]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-admin"]})


(deftest check-configuration-template-nuvla
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id            (str ct/resource-type "/" nuvla/service)
                   :resource-type nuvla/service
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :service       nuvla/service

                   :smtp-username "username"
                   :smtp-password "password"
                   :smtp-host     "host"
                   :smtp-port     465
                   :smtp-ssl      true
                   :smtp-debug    true

                   :support-email "admin@example.org"

                   :stripe-api-key "sk_test_xxx"

                   :conditions-url "https://nuvla.io/terms/tos"}]

    (stu/is-valid ::ct-nuvla/schema cfg)

    ;; mandatory keys
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::ct-nuvla/schema (dissoc cfg k)))

    ;; optional keys
    (doseq [k #{:smtp-username :smtp-password :smtp-host :smtp-port :smtp-ssl :smtp-debug
                :support-email :stripe-api-key :conditions-url}]
      (stu/is-valid ::ct-nuvla/schema (dissoc cfg k)))))
