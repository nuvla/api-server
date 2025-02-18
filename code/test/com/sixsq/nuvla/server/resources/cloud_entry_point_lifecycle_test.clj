(ns com.sixsq.nuvla.server.resources.cloud-entry-point-lifecycle-test
  (:require
    [clojure.test :refer [deftest testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.cloud-entry-point :as t]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.spec.cloud-entry-point :as cep]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [qbits.spandex :as spandex]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  ;; cloud-entry-point will have been initialized in the test server fixture.

  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane-updater")]

    ; retrieve root resource (anonymously should work) and verify schema
    (let [cep (-> session-anon
                  (request base-uri)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-resource-uri t/resource-type)
                  (ltu/is-operation-absent :edit)
                  (ltu/is-operation-absent :delete)
                  (ltu/body))]

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
                 :body (j/write-value-as-string {:name "dummy"}))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; update the entry, verify updated doc is returned
    ;; must be done as administrator
    (-> session-admin
        (request base-uri
                 :request-method :put
                 :body (j/write-value-as-string {:name "dummy"}))
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
        (ltu/is-key-value :name "dummy"))

    (testing "cors preflight check should be authorized"
      (-> session-anon
          (request base-uri
                   :request-method :options)
          (ltu/body->edn)
          (ltu/is-status 204)))

    (testing "error is returned to user when ES throws exception"
      (let [error-msg "unexpected exception ...: Connection refused"]
        (with-redefs [spandex/request (fn [_ _]
                                        (throw (Exception. error-msg)))]
          (-> session-anon
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 500)
              (ltu/message-matches (re-pattern (str ".*" error-msg)))))))))


(deftest bad-methods
  (ltu/verify-405-status [[base-uri :delete]
                          [base-uri :post]]))



