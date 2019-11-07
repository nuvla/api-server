(ns sixsq.nuvla.server.resources.callback-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context callback/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists callback/resource-type))


(deftest lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header
                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "group/nuvla-anon")]


    ;; admin collection query should succeed but be empty
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit)
        (ltu/is-operation-absent :execute))

    ;; user collection query should not succeed
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anonymous collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))


    ;; create a callback as an admin
    (let [create-test-callback {:action          "action-name"
                                :target-resource {:href "email/1234579abcdef"}
                                :state           "SUCCEEDED"} ;; state should be ignored

          resp-test            (-> session-admin
                                   (request base-uri
                                            :request-method :post
                                            :body (json/write-str create-test-callback))
                                   (ltu/body->edn)
                                   (ltu/is-status 201))

          id-test              (ltu/body-resource-id resp-test)

          location-test        (str p/service-context (-> resp-test ltu/location))

          test-uri             (str p/service-context id-test)]

      (is (= location-test test-uri))

      ;; admin should be able to see the callback
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-absent :edit)
          (ltu/is-operation-present :execute))

      ;; user cannot directly see the callback
      (-> session-user
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; check contents and editing
      (let [reread-test-callback       (-> session-admin
                                           (request test-uri)
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/body))
            original-updated-timestamp (:updated reread-test-callback)]

        (is (= (ltu/strip-unwanted-attrs reread-test-callback)
               (ltu/strip-unwanted-attrs (assoc create-test-callback :state "WAITING"))))

        ;; mark callback as failed
        (utils/callback-failed! id-test)
        (let [callback (-> session-admin
                           (request test-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-operation-absent :execute)
                           (ltu/body))]
          (is (= "FAILED" (:state callback)))
          (is (not= original-updated-timestamp (:updated callback))))

        ;; mark callback as succeeded
        (utils/callback-succeeded! id-test)
        (let [callback (-> session-admin
                           (request test-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-operation-absent :execute)
                           (ltu/body))]
          (is (= "SUCCEEDED" (:state callback)))
          (is (not= original-updated-timestamp (:updated callback)))))

      ;; search
      (-> session-admin
          (request base-uri
                   :request-method :put
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-count 1)
          (ltu/is-status 200))

      ;; delete
      (-> session-anon
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-user
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; callback must be deleted
      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id callback/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]])))
