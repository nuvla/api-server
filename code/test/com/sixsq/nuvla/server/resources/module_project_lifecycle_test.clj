(ns com.sixsq.nuvla.server.resources.module-project-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module.utils :as utils]
    [com.sixsq.nuvla.server.resources.spec.module :as module-spec]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context module/resource-type))


(def timestamp "1964-08-25T10:00:00.00Z")

(deftest lifecycle-project
  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header
                              "user/jane user/jane group/nuvla-user group/nuvla-anon")

        valid-entry   {:resource-type module/resource-type
                       :created       timestamp
                       :updated       timestamp
                       :parent-path   ""
                       :path          "example"
                       :subtype       "project"}]

    ;; create: NOK for anon
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; queries: NOK for anon
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    (doseq [session [session-admin session-user]]
      (let [resources (-> session
                          (request base-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body)
                          :resources)]
        (is (empty? (utils/filter-project-apps-sets resources))
            (str "No modules should be present apart from " utils/project-apps-sets))))

    ;; adding, retrieving and deleting entry as user should succeed
    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))

          abs-uri (str p/service-context uri)]

      ;; retrieve: NOK for anon
      (-> session-anon
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; edit: NOK for anon
      (-> session-anon
          (request abs-uri
                   :request-method :put
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-anon
          (request abs-uri
                   :request-method :put
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; delete: NOK for anon
      (-> session-anon
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the resource was deleted.
      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404))

      ;; delete: NOK if project has children
      (let [uri         (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-entry))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
            abs-uri     (str p/service-context uri)

            valid-app   {:parent-path   "example"
                         :path          "example/app"
                         :subtype       module-spec/subtype-app
                         :compatibility "docker-compose"}
            app-uri     (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-app))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
            app-abs-uri (str p/service-context app-uri)]

        (-> session-user
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403)
            (ltu/message-matches "Cannot delete project with children"))

        (-> session-user
            (request app-abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id module/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
