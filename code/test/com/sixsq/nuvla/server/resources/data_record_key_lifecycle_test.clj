(ns com.sixsq.nuvla.server.resources.data-record-key-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.data-record-key :as t]
    [com.sixsq.nuvla.server.resources.data-record-key-prefix :as san]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-entry
  {:name        "Test Attribute"
   :description "An attribute for tests."
   :prefix      "example-org"
   :key         "test-attribute"
   :subtype     "string"})


(def invalid-entry
  (merge valid-entry {:other "BAD"}))


(def valid-namespace
  {:prefix "example-org"
   :uri    "https://schema-org/a/b/c.md"})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    ;; create namespace
    (-> session-admin
        (request (str p/service-context san/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anonymous query should also fail
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ; adding the same attribute twice should fail
    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]


      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 409))

      (-> session-user
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
