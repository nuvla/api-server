(ns sixsq.nuvla.server.resources.spec.email-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.email :as t]
    [sixsq.nuvla.server.resources.spec.email :as email]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-email-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        email     {:id            (str t/resource-type "/abcdef")
                   :resource-type t/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl
                   :address       "user@example.com"
                   :validated     false}]

    (stu/is-valid ::email/schema email)

    (stu/is-invalid ::email/schema (assoc email :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl :address}]
      (stu/is-invalid ::email/schema (dissoc email attr)))

    (doseq [attr #{:validated}]
      (stu/is-valid ::email/schema (dissoc email attr)))))
