(ns sixsq.nuvla.server.resources.spec.voucher-discipline-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.voucher-discipline :as voucher-discipline]
    [sixsq.nuvla.server.resources.voucher-discipline :as t]))


(def valid-acl {:owners   ["group/ocre-user"]
                :edit-acl ["group/ocre-user"]})


(deftest check-voucher-discipline-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        voucher-discipline   {:id               (str t/resource-type "/abcdef")
                   :name             "my voucher-discipline"
                   :description      "description of my voucher-discipline"
                   :resource-type    t/resource-type
                   :created          timestamp
                   :updated          timestamp
                   :acl              valid-acl

                   }]

    (stu/is-valid ::voucher-discipline/schema voucher-discipline)

    (stu/is-invalid ::voucher-discipline/schema (assoc voucher-discipline :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::voucher-discipline/schema (dissoc voucher-discipline attr)))

    (doseq [attr #{:name :description}]
      (stu/is-valid ::voucher-discipline/schema (dissoc voucher-discipline attr)))))
