(ns sixsq.nuvla.server.resources.session-template-password-reset-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-lifecycle-test-utils :as stu]
    [sixsq.nuvla.server.resources.session-template-password-reset :as password-reset]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context st/resource-type))


(def valid-template {:method       password-reset/authn-method
                     :instance     password-reset/authn-method
                     :name         "Password"
                     :description  "Password Authentication via Username/Password"
                     :username     "username"
                     :new-password "new-password"
                     :acl          st/resource-acl})


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-type "-" password-reset/resource-url)
                              (str st/resource-type "-" password-reset/resource-url "-create")))


(deftest lifecycle
  (stu/check-existing-session-template base-uri valid-template)
  (stu/session-template-lifecycle base-uri valid-template))
