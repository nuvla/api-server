(ns sixsq.nuvla.server.resources.module-applications-sets-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module-applications-sets :as t]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-entry {:id                (str t/resource-type "/module-applications-sets-uuid")
                  :resource-type     t/resource-type
                  :created           timestamp
                  :updated           timestamp
                  :acl               valid-acl

                  :author            "someone"
                  :commit            "wip"

                  :applications-sets [{:name         "x"
                                       :applications [{:id      "module/x"
                                                       :version 0}]}]})


(deftest lifecycle

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header
                              "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    ;; create: NOK for anon, users
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; queries: OK for admin, NOK for others
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 0))

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))

          abs-uri (str p/service-context uri)]

      ;; retrieve: OK for admin; NOK for others
      (doseq [session [session-anon session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; delete: OK for admin; NOK for others
      (doseq [session [session-anon session-user]]
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
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))