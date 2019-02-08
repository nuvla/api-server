(ns sixsq.nuvla.server.resources.connector-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector :as c]
    [sixsq.nuvla.server.resources.connector-template :as ct]
    [sixsq.nuvla.server.resources.connector-template-alpha-example :as example]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def collection-uri (str p/service-context c/resource-type))


(deftest lifecycle

  (let [href (str ct/resource-type "/" example/cloud-service-type)
        template-url (str p/service-context ct/resource-type "/" example/cloud-service-type)

        session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "jane USER")
        session-admin (header session-anon authn-info-header "root ADMIN")

        resp (-> session-admin
                 (request template-url)
                 (ltu/body->edn)
                 (ltu/is-status 200))
        template (get-in resp [:response :body])
        valid-create {:template (ltu/strip-unwanted-attrs (merge template {:alphaKey     2001
                                                                           :instanceName "alpha-omega"}))}
        href-create {:template {:href         href
                                :alphaKey     3001
                                :instanceName "alpha-omega"}}
        invalid-create (assoc-in valid-create [:template :invalid] "BAD")]

    ;; anonymous create should fail
    (-> session-anon
        (request collection-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user create should also fail
    (-> session-user
        (request collection-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; admin create with invalid template fails
    (-> session-admin
        (request collection-uri
                 :request-method :post
                 :body (json/write-str invalid-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; full connector lifecycle as administrator should work
    (let [uri (-> session-admin
                  (request collection-uri
                           :request-method :post
                           :body (json/write-str valid-create))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          resource-uri (str p/service-context uri)]

      ;; admin get succeeds
      (-> session-admin
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user get succeeds
      (-> session-user
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user update fails
      (-> session-user
          (request resource-uri
                   :request-method :put
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user delete fails
      (-> session-user
          (request resource-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user query succeeds
      (-> session-user
          (request collection-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; anonymous query fails
      (-> session-anon
          (request collection-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; admin query succeeds
      (let [entries (-> session-admin
                        (request collection-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri c/collection-uri)
                        (ltu/is-count #(= 1 %))
                        (ltu/entries))]
        (is ((set (map :id entries)) uri))

        ;; verify that all entries are accessible
        (let [pair-fn (juxt :id #(str p/service-context (:id %)))
              pairs (map pair-fn entries)]
          (doseq [[id entry-uri] pairs]
            (-> session-admin
                (request entry-uri)
                (ltu/body->edn)
                (ltu/is-operation-present "delete")
                (ltu/is-operation-present "edit")
                (ltu/is-operation-absent "activate")
                (ltu/is-operation-absent "quarantine")
                (ltu/is-status 200)
                (ltu/is-id id)))))

      ;; admin delete succeeds
      (-> session-admin
          (request resource-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))

    ;; abbreviated lifecycle using href to template instead of copy
    (let [uri (-> session-admin
                  (request collection-uri
                           :request-method :post
                           :body (json/write-str href-create))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; admin delete succeeds
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id c/resource-type))]
    (ltu/verify-405-status [[collection-uri :options]
                            [collection-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
