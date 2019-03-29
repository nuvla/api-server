(ns sixsq.nuvla.server.resources.session-template-api-key-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-api-key :as api-key]
    [sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context st/resource-type))


(def valid-template {:method      api-key/authn-method
                     :instance    api-key/authn-method
                     :name        "API Key"
                     :description "Authentication with API Key and Secret"
                     :key         "key"
                     :secret      "secret"
                     :acl         st/resource-acl})

(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" api-key/resource-url)))


(deftest lifecycle
  (stu/check-existing-session-template base-uri valid-template)
  (stu/session-template-lifecycle base-uri valid-template))
