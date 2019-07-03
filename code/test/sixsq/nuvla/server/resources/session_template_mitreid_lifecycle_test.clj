(ns sixsq.nuvla.server.resources.session-template-mitreid-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [sixsq.nuvla.server.resources.session-template-mitreid :as mitreid]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context st/resource-type))


(def valid-template {:method      mitreid/authn-method
                     :instance    mitreid/authn-method
                     :name        "MITREid Connect"
                     :description "External Authentication via MITREid Connect Protocol"
                     :acl         st/resource-acl})


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" mitreid/resource-url)))


(deftest lifecycle
  (stu/session-template-lifecycle base-uri valid-template))
