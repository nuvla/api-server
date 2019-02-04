(ns com.sixsq.slipstream.ssclj.resources.spec.email-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.email :as t]
    [com.sixsq.slipstream.ssclj.resources.spec.email :as email]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-email-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        email {:id          (str t/resource-url "/abcdef")
               :resourceURI t/resource-uri
               :created     timestamp
               :updated     timestamp
               :acl         valid-acl
               :address     "user@example.com"
               :validated   false}]

    (stu/is-valid ::email/schema email)

    (stu/is-invalid ::email/schema (assoc email :bad "value"))

    (doseq [attr #{:id :resourceURI :created :updated :acl :address}]
      (stu/is-invalid ::email/schema (dissoc email attr)))

    (doseq [attr #{:validated}]
      (stu/is-valid ::email/schema (dissoc email attr)))))
