(ns com.sixsq.nuvla.server.resources.session-template-oidc-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.session-template :as st]
    [com.sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [com.sixsq.nuvla.server.resources.session-template-oidc :as oidc]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context st/resource-type))


(def valid-template {:method      oidc/authn-method
                     :instance    oidc/authn-method
                     :name        "OpenID Connect"
                     :description "External Authentication via OpenID Connect Protocol"
                     :acl         st/resource-acl})


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" oidc/resource-url)))


(deftest lifecycle
  (stu/session-template-lifecycle base-uri valid-template))
