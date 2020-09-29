(ns sixsq.nuvla.server.resources.pricing-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.pricing :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (if-not (env/env :stripe-api-key)
    (log/error "Customer lifecycle is not tested because lack of stripe-api-key!")
    (let [session-anon  (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

      ;; admin/user query succeeds but is empty
      (doseq [session [session-admin session-user session-anon]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)))

      ;; anon create must fail
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user can't create catalogue
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403))

      (let [admin-uri (-> session-admin
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str {}))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location-url))]

        ;; admin can't recreate catalogue
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str {}))
            (ltu/body->edn)
            (ltu/is-status 409))

        ;; admin should see 1 pricing resources
        (-> session-anon
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-resource-uri t/collection-type)
            (ltu/is-count 1))

        ;; verify contents of admin pricing
        (let [op-regenerate (-> session-admin
                                (request admin-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/is-operation-present :delete)
                                (ltu/is-operation-present :regenerate)
                                (ltu/get-op-url :regenerate))]

          (-> session-admin
              (request op-regenerate
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-user
              (request admin-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 403))

          (-> session-admin
              (request admin-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200)))

        ))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
