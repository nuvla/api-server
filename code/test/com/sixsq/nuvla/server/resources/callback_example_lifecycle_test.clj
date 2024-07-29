(ns com.sixsq.nuvla.server.resources.callback-example-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback-example :as example]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context callback/resource-type))

(deftest lifecycle
  (let [session                  (-> (ltu/ring-app)
                                     session
                                     (content-type "application/json"))
        session-admin            (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon             (header session authn-info-header "group/nuvla-anon")

        create-callback-succeeds {:action          example/action-name
                                  :target-resource {:href "example/resource-x"}
                                  :data            {:ok? true}}

        create-callback-fails    {:action          example/action-name
                                  :target-resource {:href "example/resource-y"}
                                  :data            {:ok? false}}

        uri-succeeds             (str p/service-context (-> session-admin
                                                            (request base-uri
                                                                     :request-method :post
                                                                     :body (json/write-str create-callback-succeeds))
                                                            (ltu/body->edn)
                                                            (ltu/is-status 201)
                                                            (ltu/body)
                                                            :resource-id))

        trigger-succeeds         (str p/service-context (-> session-admin
                                                            (request uri-succeeds)
                                                            (ltu/body->edn)
                                                            (ltu/is-status 200)
                                                            (ltu/get-op "execute")))

        uri-fails                (str p/service-context (-> session-admin
                                                            (request base-uri
                                                                     :request-method :post
                                                                     :body (json/write-str create-callback-fails))
                                                            (ltu/body->edn)
                                                            (ltu/is-status 201)
                                                            (ltu/body)
                                                            :resource-id))

        trigger-fails            (str p/service-context (-> session-admin
                                                            (request uri-fails)
                                                            (ltu/body->edn)
                                                            (ltu/is-status 200)
                                                            (ltu/get-op "execute")))]

    ;; anon should be able to trigger the callbacks
    (-> session-anon
        (request trigger-succeeds)
        (ltu/body->edn)
        (ltu/is-status 200))

    (-> session-anon
        (request trigger-fails)
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; retriggering the callbacks must fail with 409
    (-> session-anon
        (request trigger-succeeds)
        (ltu/body->edn)
        (ltu/is-status 409))

    (-> session-anon
        (request trigger-fails)
        (ltu/body->edn)
        (ltu/is-status 409))

    ;; delete
    (-> session-admin
        (request uri-succeeds
                 :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))

    (-> session-admin
        (request uri-fails
                 :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; ensure that an unknown callback returns 404
    (-> session-anon
        (request trigger-succeeds)
        (ltu/body->edn)
        (ltu/is-status 404))))


(deftest lifecycle-tries-left
  (let [session                  (-> (ltu/ring-app)
                                     session
                                     (content-type "application/json"))
        session-admin            (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon             (header session authn-info-header "group/nuvla-anon")

        create-callback-succeeds {:action          example/action-name
                                  :target-resource {:href "example/resource-x"}
                                  :data            {:ok?           true
                                                    :change-state? false}
                                  :tries-left      2}

        uri-succeeds             (str p/service-context (-> session-admin
                                                            (request base-uri
                                                                     :request-method :post
                                                                     :body (json/write-str create-callback-succeeds))
                                                            (ltu/body->edn)
                                                            (ltu/is-status 201)
                                                            (ltu/body)
                                                            :resource-id))

        trigger-succeeds         (str p/service-context (-> session-admin
                                                            (request uri-succeeds)
                                                            (ltu/body->edn)
                                                            (ltu/is-status 200)
                                                            (ltu/get-op "execute")))]

    ;; anon should be able to trigger the callbacks
    (-> session-anon
        (request trigger-succeeds)
        (ltu/body->edn)
        (ltu/is-status 200))

    (-> session-admin
        (request uri-succeeds)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :state "WAITING")
        (ltu/is-key-value :tries-left 1))

    ;; retriggering the callbacks a second time should succeed
    (-> session-anon
        (request trigger-succeeds)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; retriggering the callback a third time should fail
    (-> session-anon
        (request trigger-succeeds)
        (ltu/body->edn)
        (ltu/is-status 409))))
