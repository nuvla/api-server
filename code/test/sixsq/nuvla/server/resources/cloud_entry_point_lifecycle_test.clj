(ns sixsq.nuvla.server.resources.cloud-entry-point-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.cloud-entry-point :as t]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.spec.cloud-entry-point :as cep]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  ;; cloud-entry-point will have been initialized in the test server fixture.

  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane-updater")]

    ; retrieve root resource (anonymously should work) and verify schema
    (let [cep (-> session-anon
                  (request base-uri)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-resource-uri t/resource-type)
                  (ltu/is-operation-absent :edit)
                  (ltu/is-operation-absent :delete)
                  :response
                  :body)]

      (stu/is-valid ::cep/resource cep))

    ;; retrieve root resource (root should have edit rights)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri t/resource-type)
        (ltu/is-operation-present :edit)
        (ltu/is-operation-absent :delete))

    ;; updating root resource as user should fail
    (-> session-user
        (request base-uri
                 :request-method :put
                 :body (json/write-str {:name "dummy"}))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; update the entry, verify updated doc is returned
    ;; must be done as administrator
    (-> session-admin
        (request base-uri
                 :request-method :put
                 :body (json/write-str {:name "dummy"}))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri t/resource-type)
        (ltu/is-operation-present :edit)
        (ltu/is-key-value :name "dummy"))

    ;; verify that subsequent reads find the right data
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri t/resource-type)
        (ltu/is-operation-absent :edit)
        (ltu/is-key-value :name "dummy"))))


(deftest bad-methods
  (ltu/verify-405-status [[base-uri :options]
                          [base-uri :delete]
                          [base-uri :post]]))



