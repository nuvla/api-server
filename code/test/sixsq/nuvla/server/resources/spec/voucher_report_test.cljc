(ns sixsq.nuvla.server.resources.spec.voucher-report-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.voucher-report :as voucher-report]
    [sixsq.nuvla.server.resources.voucher :as t]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-voucher-report-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        voucher-report   {:id               (str t/resource-type "/abcdef")
                          :name             "my voucher-report"
                          :description      "description of my voucher-report"
                          :resource-type    t/resource-type
                          :created          timestamp
                          :updated          timestamp
                          :acl              valid-acl

                          :supplier         "user/abcdef01-abcd-abcd-abcd-abcdef012340"
                          :parent           "voucher/abcdef01-abcd-abcd-abcd-abcdef012341"
                          :amount-left      -10.50
                          :amount-spent     55
                          :currency         "EUR"
                          :redeemed         timestamp
                          }]

    (stu/is-valid ::voucher-report/schema voucher-report)

    (stu/is-invalid ::voucher-report/schema (assoc voucher-report :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::voucher-report/schema (dissoc voucher-report attr)))

    (doseq [attr #{:name :description :user :activated :redeemed :expiry :wave :batch}]
      (stu/is-valid ::voucher-report/schema (dissoc voucher-report attr)))))
