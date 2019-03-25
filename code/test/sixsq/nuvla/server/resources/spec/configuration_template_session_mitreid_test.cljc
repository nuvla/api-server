(ns sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid :as cts-mitreid]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id             (str ct/resource-type "/session-mitreid-test-instance")
              :resource-type  p/service-context
              :created        timestamp
              :updated        timestamp
              :acl            valid-acl

              :service        "session-mitreid"
              :instance       "test-instance"

              :clientID       "FAKE_CLIENT_ID"
              :clientSecret   "MyMITREidClientSecret"
              :authorizeURL   "https://authorize.mitreid.com/authorize"
              :tokenURL       "https://token.mitreid.com/token"
              :userProfileURL "https://userinfo.mitreid.com/api/user/me"
              :publicKey      "fake-public-key-value"}]

    (stu/is-valid ::cts-mitreid/schema root)

    (stu/is-invalid ::cts-mitreid/schema (assoc root :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated :acl
                :service :instance
                :clientID :clientSecret :authorizeURL :tokenURL :userProfileURL :publicKey}]
      (stu/is-invalid ::cts-mitreid/schema (dissoc root k)))))
