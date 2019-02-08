(ns sixsq.nuvla.server.resources.service-attribute-namespace-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.service-attribute-namespace :refer :all]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context resource-type))

(def valid-namespace
  {:prefix "schema-org"
   :uri    "https://schema-org/a/b/c.md"})

(def namespace-same-prefix
  {:prefix "schema-org"
   :uri    "https://schema-com/z"})
(def namespace-same-uri
  {:prefix "schema-com"
   :uri    "https://schema-org/a/b/c.md"})
(def another-valid-namespace
  {:prefix "schema-com"
   :uri    "https://schema-com/z"})

(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user create should fail
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (ltu/body->edn)
        (ltu/is-status 403))

    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-namespace))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context uri)
          doc (-> session-user
                  (request abs-uri)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (get-in [:response :body]))]

      (is (= "schema-org" (:prefix doc)))
      (is (= "https://schema-org/a/b/c.md" (:uri doc)))
      (is (= "service-attribute-namespace/schema-org" uri))

      (-> session-user
          (request "/api/service-attribute-namespace")
          (ltu/body->edn)
          (ltu/is-status 200)
          (get-in [:response :body]))

      ;; trying to create another namespace with same name is forbidden
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str namespace-same-prefix))
          (ltu/body->edn)
          (ltu/is-status 409)
          (get-in [:response :body :message])
          (= (str "conflict with " uri))
          is)

      ;; trying to create another namespace with same uri is forbidden
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str namespace-same-uri))
          (ltu/body->edn)
          (ltu/is-status 409)
          (get-in [:response :body :message])
          (= (str "conflict with " uri))
          is)

      ;; trying to create another namespace with other name and URI is ok
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str another-valid-namespace))
          (ltu/body->edn)
          (ltu/is-status 201))

      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
