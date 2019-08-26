(ns sixsq.nuvla.server.resources.spec.session-template-mitreid-token-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.spec.session-template-mitreid-token :as st-mitreid-token]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-session-template-mitreid-token-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        cfg       {:id            (str st/resource-type "/mitreid-token")
                   :resource-type st/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :method        "mitreid-token"
                   :instance      "mitreid-token"
                   :group         "Federated Identity"
                   :redirect-url  "https://nuv.la/webui/profile"

                   :token         "some-compressed-mitreid-token-value"}]

    (stu/is-valid ::st-mitreid-token/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :instance :token}]
      (stu/is-invalid ::st-mitreid-token/schema (dissoc cfg attr)))

    (doseq [attr #{:group :redirect-url}]
      (stu/is-valid ::st-mitreid-token/schema (dissoc cfg attr)))))
