(ns sixsq.nuvla.server.resources.nuvlabox-identifier-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-identifier :as nuvlabox-identifier]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nuvlabox-identifier/resource-type))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00Z")


(def test-identifier "some-nuvlabox-identifer")


(def valid-entry {:id          (str nuvlabox-identifier/resource-type "/hashed-identifier")
                  ::resource-type nuvlabox-identifier/resource-type
                  :created     timestamp
                  :updated     timestamp
                  :acl         valid-acl

                  :identifier  test-identifier
                  :series      "examples"})


(deftest check-metadata
  (mdtu/check-metadata-exists nuvlabox-identifier/resource-type))


(deftest lifecycle

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-jane (header session-anon authn-info-header "jane group/nuvla-user group/nuvla-anon")
        session-tarzan (header session-anon authn-info-header "tarzan group/nuvla-user group/nuvla-anon")]

    ;; create: NOK for anon, users
    (doseq [session [session-anon session-jane session-tarzan]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; queries: OK only for admin, 403 for everyone else
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 0))


    (doseq [session [session-anon session-jane session-tarzan]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))


    ;; adding, retrieving and deleting entry as administrator should succeed
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))

          abs-uri (str p/service-context uri)]

      ;; retrieve: OK for admin, 403 for everyone else
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      (doseq [session [session-anon session-jane session-tarzan]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403)))

      ;; check content of the resource
      (let [expected-id (str nuvlabox-identifier/resource-type "/" (u/md5 (:identifier valid-entry)))
            resource (-> session-admin
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         :response
                         :body)]

        (is (= {:id         expected-id
                :identifier test-identifier}
               (select-keys resource #{:id :identifier :nuvlabox}))))

      ;; adding the same resource a second time must fail
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 409))

      ;; delete: OK for admin; NOK for others
      (doseq [session [session-anon session-jane session-tarzan]]
        (-> session
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the resource was deleted.
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nuvlabox-identifier/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
