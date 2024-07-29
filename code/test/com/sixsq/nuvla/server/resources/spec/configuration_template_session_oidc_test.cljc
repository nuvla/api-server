(ns com.sixsq.nuvla.server.resources.spec.configuration-template-session-oidc-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-oidc :as cts-oidc]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id                    (str ct/resource-type "/session-oidc-test-instance")
                   :resource-type         ct/resource-type
                   :created               timestamp
                   :updated               timestamp
                   :acl                   valid-acl

                   :service               "session-oidc"
                   :instance              "test-instance"

                   :client-id             "FAKE_CLIENT_ID"
                   :jwks-url              "token-url"
                   :authorize-url         "authorize-url"
                   :token-url             "token-url"
                   :client-secret         "MyOIDCClientSecret"
                   :redirect-url-resource "callback"}]

    (stu/is-valid ::cts-oidc/schema root)

    (stu/is-invalid ::cts-oidc/schema (assoc root :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated :acl :service :instance
                :client-id :jwks-url :authorize-url :token-url}]
      (stu/is-invalid ::cts-oidc/schema (dissoc root k)))

    (doseq [k #{:client-secret}]
      (stu/is-valid ::cts-oidc/schema (dissoc root k)))))
