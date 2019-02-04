(ns com.sixsq.slipstream.ssclj.resources.session-template-mitreid-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.session-template :as st]
    [com.sixsq.slipstream.ssclj.resources.session-template-lifecycle-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.resources.session-template-mitreid :as mitreid]
    [com.sixsq.slipstream.ssclj.util.metadata-test-utils :as mdtu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase st/resource-name)))


(def valid-template {:method      mitreid/authn-method
                     :instance    mitreid/authn-method
                     :name        "MITREid Connect"
                     :description "External Authentication via MITREid Connect Protocol"
                     :acl         st/resource-acl})


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-url "-" mitreid/resource-url)))


(deftest lifecycle
  (stu/session-template-lifecycle base-uri valid-template))
