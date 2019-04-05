(ns sixsq.nuvla.server.resources.spec.voucher-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.voucher :as t]
    [sixsq.nuvla.server.resources.spec.voucher :as voucher]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-voucher-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        voucher {:id                 (str t/resource-type "/abcdef")
                 :name               "my voucher"
                 :description        "description of my voucher"
                 :resource-type      t/resource-type
                 :created            timestamp
                 :updated            timestamp
                 :acl                valid-acl

                 :owner              {:href "user/my-user-uuid"}
                 :amount             50.0
                 :currency           "EUR"
                 :code               "vH72Hks209"
                 :state              "new"
                 :target-audience    "scientists@university.com"
                 :expiration-date    timestamp
                 :activated          timestamp
                 :redeemed           timestamp
                 :user               {:href "user/my-user-uuid"}
                 :wave-id            "wave id"
                 :batch-reference    "abc"
                 }]

    (stu/is-valid ::voucher/schema voucher)

    (stu/is-invalid ::voucher/schema (assoc voucher :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::voucher/schema (dissoc voucher attr)))

    (doseq [attr #{:name :description :user :activated :redeemed :expiration-date :wave-id :batch-reference}]
      (stu/is-valid ::voucher/schema (dissoc voucher attr)))))
