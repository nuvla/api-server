(ns sixsq.nuvla.server.resources.spec.voucher-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.voucher :as voucher]
    [sixsq.nuvla.server.resources.voucher :as t]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-voucher-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        voucher   {:id               (str t/resource-type "/abcdef")
                   :name             "my voucher"
                   :description      "description of my voucher"
                   :resource-type    t/resource-type
                   :created          timestamp
                   :updated          timestamp
                   :acl              valid-acl

                   :owner            "user/abcdef01-abcd-abcd-abcd-abcdef012340"
                   :amount           50.0
                   :currency         "EUR"
                   :code             "vH72Hks209"
                   :state            "NEW"
                   :researcher-email "scientists@university.com"
                   :researcher-name  "john doe"
                   :country          "PT"
                   :discipline       "science"
                   :institute        "university"
                   :service-info-url "https://url.com"
                   :supplier         "supplier A"
                   :distributor      "distributor A"
                   :platform         "cloud A"
                   :expiry           timestamp
                   :activated        timestamp
                   :redeemed         timestamp
                   :user             "user/abcdef01-abcd-abcd-abcd-abcdef012345"
                   :wave             "wave id"
                   :batch            "abc"
                   }]

    (stu/is-valid ::voucher/schema voucher)

    (stu/is-invalid ::voucher/schema (assoc voucher :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::voucher/schema (dissoc voucher attr)))

    (doseq [attr #{:name :description :user :activated :redeemed :expiry :wave :batch :country
                   :discipline :researcher-email :researcher-name :institute}]
      (stu/is-valid ::voucher/schema (dissoc voucher attr)))))
