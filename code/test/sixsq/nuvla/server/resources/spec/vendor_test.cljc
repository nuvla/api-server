(ns sixsq.nuvla.server.resources.spec.vendor-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.vendor :as vendor]
    [sixsq.nuvla.server.resources.vendor :as t]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-vendor-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        vendor    {:id            (str t/resource-type "/abcdef")
                   :resource-type t/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :account-id "some-account-id"}]

    (stu/is-valid ::vendor/schema vendor)

    (stu/is-invalid ::vendor/schema (assoc vendor :bad "value"))

    ;; mandatory parameters
    (doseq [attr #{:id :resource-type :created :updated :acl :account-id}]
      (stu/is-invalid ::vendor/schema (dissoc vendor attr)))))
