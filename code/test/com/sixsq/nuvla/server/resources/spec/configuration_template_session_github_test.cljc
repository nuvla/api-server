(ns com.sixsq.nuvla.server.resources.spec.configuration-template-session-github-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-github :as cts-github]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id            (str ct/resource-type "/session-github-test-instance")
                   :resource-type ct/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :service       "session-github"
                   :instance      "test-instance"

                   :client-id     "FAKE_CLIENT_ID"
                   :client-secret "MyGitHubClientSecret"}]

    (stu/is-valid ::cts-github/schema root)

    (stu/is-invalid ::cts-github/schema (assoc root :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated :acl
                :service :instance :client-id :client-secret}]
      (stu/is-invalid ::cts-github/schema (dissoc root k)))))
