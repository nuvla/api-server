(ns com.sixsq.slipstream.ssclj.resources.service-attribute-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as t]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.service-attribute :refer :all]
    [com.sixsq.slipstream.ssclj.resources.service-attribute-namespace :as san]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase resource-name)))

(def valid-entry
  {:name          "Test Attribute"
   :description   "An attribute for tests."
   :prefix        "example-org"
   :attributeName "test-attribute"
   :type          "string"})

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
        (request (str p/service-context san/resource-url)
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
          abs-uri (str p/service-context (u/de-camelcase uri))]


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
  (let [resource-uri (str p/service-context (u/new-resource-id resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
