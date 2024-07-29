(ns com.sixsq.nuvla.server.resources.spec.user-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [com.sixsq.nuvla.server.resources.spec.user :as user]
    [com.sixsq.nuvla.server.resources.user :as t]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-user-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                  (str t/resource-type "/uuid")

                   :resource-type       t/resource-type

                   :created             timestamp
                   :updated             timestamp

                   :credential-password "credential/d3167d53-0138-4754-b8fd-df8119474e7f"

                   :credential-totp     "credential/e3167d53-0138-4754-b8fd-df8119474e7f"

                   :email               "email/uuid"

                   :method              "direct"

                   :state               "ACTIVE"

                   :auth-method-2fa     "none"

                   :acl                 valid-acl}]

    (stu/is-valid ::user/schema cfg)
    (stu/is-invalid ::user/schema (assoc cfg :unknown "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl :state}]
      (stu/is-invalid ::user/schema (dissoc cfg attr)))

    (doseq [attr #{:name :method :credential-password
                   :credential-totp :email :auth-method-2fa}]
      (stu/is-valid ::user/schema (dissoc cfg attr)))))
