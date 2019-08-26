(ns sixsq.nuvla.server.resources.session-template-github-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-github :as github]
    [sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context st/resource-type))


(def valid-template {:method      github/authn-method
                     :instance    github/authn-method
                     :name        "GitHub"
                     :description "External Authentication with GitHub Credentials"
                     :acl         st/resource-acl})


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" github/resource-url)))


(deftest lifecycle
  (stu/session-template-lifecycle base-uri valid-template))
