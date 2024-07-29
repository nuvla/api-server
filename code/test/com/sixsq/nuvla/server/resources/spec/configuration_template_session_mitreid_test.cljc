(ns com.sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid :as cts-mitreid]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id               (str ct/resource-type "/session-mitreid-test-instance")
                   :resource-type    ct/resource-type
                   :created          timestamp
                   :updated          timestamp
                   :acl              valid-acl

                   :service          "session-mitreid"
                   :instance         "test-instance"

                   :client-id        "FAKE_CLIENT_ID"
                   :client-secret    "MyMITREidClientSecret"
                   :authorize-url    "https://authorize.mitreid.com/authorize"
                   :token-url        "https://token.mitreid.com/token"
                   :user-profile-url "https://userinfo.mitreid.com/api/user/me"
                   :public-key       "fake-public-key-value"}]

    (stu/is-valid ::cts-mitreid/schema root)

    (stu/is-invalid ::cts-mitreid/schema (assoc root :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated :acl
                :service :instance
                :client-id :client-secret :authorize-url :token-url :user-profile-url :public-key}]
      (stu/is-invalid ::cts-mitreid/schema (dissoc root k)))))
