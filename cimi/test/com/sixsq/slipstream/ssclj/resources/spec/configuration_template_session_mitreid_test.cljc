(ns com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-mitreid-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-mitreid :as cts-mitreid]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id             (str ct/resource-url "/session-mitreid-test-instance")
              :resourceURI    p/service-context
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

    (doseq [k #{:id :resourceURI :created :updated :acl
                :service :instance
                :clientID :clientSecret :authorizeURL :tokenURL :userProfileURL :publicKey}]
      (stu/is-invalid ::cts-mitreid/schema (dissoc root k)))))
