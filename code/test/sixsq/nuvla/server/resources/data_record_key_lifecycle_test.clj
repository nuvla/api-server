(ns sixsq.nuvla.server.resources.data-record-key-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record-key :refer :all]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as san]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context resource-type))


(def valid-entry
  {:name        "Test Attribute"
   :description "An attribute for tests."
   :prefix      "example-org"
   :key         "test-attribute"
   :type        "string"})


(def invalid-entry
  (merge valid-entry {:other "BAD"}))


(def valid-namespace
  {:prefix "example-org"
   :uri    "https://schema-org/a/b/c.md"})


(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; create namespace
    (-> session-admin
        (request (str p/service-context san/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (t/body->edn)
        (t/is-status 403))

    ;; anonymous query should also fail
    (-> session-anon
        (request base-uri)
        (t/body->edn)
        (t/is-status 403))

    ; adding the same attribute twice should fail
    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (t/body->edn)
                  (t/is-status 201)
                  (t/location))
          abs-uri (str p/service-context uri)]


      (-> session-user
          (request abs-uri)
          (t/body->edn)
          (t/is-status 200))

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (t/body->edn)
          (t/is-status 409))

      (-> session-user
          (request abs-uri :request-method :delete)
          (t/body->edn)
          (t/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
