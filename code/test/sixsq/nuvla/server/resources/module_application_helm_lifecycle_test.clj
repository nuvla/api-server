(ns sixsq.nuvla.server.resources.module-application-helm-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module-application-helm :as module-application-helm]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context module-application-helm/resource-type))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00.00Z")


(def invalid-entry-no-mandatory {:resource-type
                                 module-application-helm/resource-type
                                 :created timestamp
                                 :updated timestamp
                                 :acl     valid-acl

                                 :author  "someone"
                                 :commit  "wip"})


(def valid-entry-helm-repo
  (assoc invalid-entry-no-mandatory
    :helm-repo-url "https://helm.github.io/examples"
    :helm-chart-name "hello-world"))


(def valid-entry-chart-url
  (assoc invalid-entry-no-mandatory
    :helm-absolute-url "https://helm.github.io/examples/hello-world-0.1.0.tgz"))


(def invalid-entry-chart-url-and-repo
  (assoc invalid-entry-no-mandatory
    :helm-repo-url "https://helm.github.io/examples"
    :helm-absolute-url "https://helm.github.io/examples/hello-world-0.1.0.tgz"))


(def invalid-entry-chart-url-and-chart-name
  (assoc invalid-entry-no-mandatory
    :helm-chart-name "hello-world"
    :helm-absolute-url "https://helm.github.io/examples/hello-world-0.1.0.tgz"))


(def invalid-entry-chart-url-and-chart-version
  (assoc invalid-entry-no-mandatory
    :helm-chart-version "1.2.3"
    :helm-absolute-url "https://helm.github.io/examples/hello-world-0.1.0.tgz"))


(def invalid-entry-no-repo-url (dissoc valid-entry-helm-repo :helm-repo-url))
(def invalid-entry-no-chart-name (dissoc valid-entry-helm-repo :helm-chart-name))
(def invalid-entry-no-absolute-url (dissoc valid-entry-chart-url :helm-absolute-url))

(def invalid-entries [invalid-entry-no-mandatory
                      invalid-entry-no-repo-url
                      invalid-entry-no-chart-name
                      invalid-entry-no-absolute-url
                      invalid-entry-chart-url-and-repo
                      invalid-entry-chart-url-and-chart-name
                      invalid-entry-chart-url-and-chart-version])


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
                   :body (json/write-str valid-entry-helm-repo))
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
    (doseq [valid-entry [valid-entry-helm-repo valid-entry-chart-url]]
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
            (ltu/is-status 404))))

    ;; invalid entries return 400
    (doseq [invalid-entry invalid-entries]
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str invalid-entry))
          (ltu/body->edn)
          (ltu/dump)
          (ltu/is-status 400)))
    ))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id module-application-helm/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
