(ns sixsq.nuvla.server.resources.spec.user-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.user :refer :all]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-user-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id                  (str resource-type "/uuid")

                   :resource-type       resource-type

                   :created             timestamp
                   :updated             timestamp

                   :credential-password "credential/uuid"

                   :email               "email/uuid"

                   :method              "direct"

                   :state               "ACTIVE"

                   :acl                 valid-acl}]

    (stu/is-valid ::user/schema cfg)
    (stu/is-invalid ::user/schema (assoc cfg :unknown "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl :state}]
      (stu/is-invalid ::user/schema (dissoc cfg attr)))

    (doseq [attr #{:name :method :credential-password :email}]
      (stu/is-valid ::user/schema (dissoc cfg attr)))))
