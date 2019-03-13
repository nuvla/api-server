(ns sixsq.nuvla.server.resources.session-template-internal-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-internal :as internal]
    [sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context st/resource-type))


(def valid-template {:method
                                  internal/authn-method
                     :instance    internal/authn-method
                     :name        "Internal"
                     :description "Internal Authentication via Username/Password"
                     :username    "username"
                     :password    "password"
                     :acl         st/resource-acl})


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" internal/resource-url)))


(deftest lifecycle
  (stu/check-existing-session-template base-uri valid-template)
  (stu/session-template-lifecycle base-uri valid-template))
