(ns com.sixsq.nuvla.server.resources.data-record-key-prefix-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.data-record-key-prefix :as t]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


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


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (j/write-value-as-string valid-namespace))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user create should fail
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (j/write-value-as-string valid-namespace))
        (ltu/body->edn)
        (ltu/is-status 403))

    (let [uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (j/write-value-as-string valid-namespace))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)
          doc     (-> session-user
                      (request abs-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/body))]

      (is (= "schema-org" (:prefix doc)))
      (is (= "https://schema-org/a/b/c.md" (:uri doc)))
      (is (= "data-record-key-prefix/schema-org" uri))

      (-> session-user
          (request "/api/data-record-key-prefix")
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body))

      ;; trying to create another namespace with same name is forbidden
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string namespace-same-prefix))
          (ltu/body->edn)
          (ltu/is-status 409)
          (ltu/message-matches (str "conflict with " uri)))

      ;; trying to create another namespace with same uri is forbidden
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string namespace-same-uri))
          (ltu/body->edn)
          (ltu/is-status 409)
          (ltu/message-matches (str "conflict with " uri)))

      ;; trying to create another namespace with other name and URI is ok
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string another-valid-namespace))
          (ltu/body->edn)
          (ltu/is-status 201))

      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
