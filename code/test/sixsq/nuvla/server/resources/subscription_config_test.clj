(ns sixsq.nuvla.server.resources.subscription-config-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.subscription-config :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu])
  (:import
    [java.util UUID]))


(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        valid-subscription-config {:type       "notification"
                                   :collection "infrastructure-service"
                                   :category   "state"
                                   :enabled    false
                                   :method     (str "notification-method/" (str (UUID/randomUUID)))
                                   :acl        {:owners ["user/jane"]}}]
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit)))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-subscription-config))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (let [user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-subscription-config))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

          user-abs-uri (str p/service-context user-uri)]

      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      ;; verify that an edit works
      (let [notif-id (str "notification-method/" (str (UUID/randomUUID)))
            updated (assoc valid-subscription-config :method notif-id)]

        (-> session-user
            (request user-abs-uri
                     :request-method :put
                     :body (json/write-str updated))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/body))

        (let [updated-body (-> session-admin
                               (request user-abs-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

          (is (= notif-id (:method updated-body)))))

      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
