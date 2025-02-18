(ns com.sixsq.nuvla.server.resources.notification-method-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.notification-method :as t]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-kafka-fixture)

(def base-uri (str p/service-context t/resource-type))

(def valid-notif-conf {:name "Jane's email"
                       :description "Jane's email"
                       :method      "email"
                       :destination "jane@example.com"
                       :acl         {:owners ["user/jane"]}})

(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]
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
                 :body (j/write-value-as-string valid-notif-conf))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (let [user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (j/write-value-as-string valid-notif-conf))
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
      (let [method "slack"
            dest "https://slack.com"
            updated (assoc valid-notif-conf :method method :destination dest)]

        (-> session-user
            (request user-abs-uri
                     :request-method :put
                     :body (j/write-value-as-string updated))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/body))

        (let [updated-body (-> session-admin
                               (request user-abs-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

          (is (= method (:method updated-body)))
          (is (= dest (:destination updated-body)))))

      ;; user can delete
      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))

(deftest action-test
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    (let [user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (j/write-value-as-string valid-notif-conf))
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

      (let [test-op (-> session-user
                        (request user-abs-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-operation-present :test)
                        (ltu/get-op-url :test))]
        (-> session-user
            (request test-op
                     :request-method :post
                     :body (j/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 201)
            (ltu/message-matches t/test-response-message)))

      (ltu/refresh-es-indices)

      (let [event (-> session-user
                      (request "/api/event")
                      (ltu/body->edn)
                      (ltu/is-count 1)
                      (ltu/entries)
                      first)
            resource-content (-> event :content :resource :content)]
        (is (= "test.notification" (:name event)))
        (is (= (:method valid-notif-conf) (:method resource-content)))
        (is (= (:destination valid-notif-conf) (:destination resource-content)))

      ;; user can delete
      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))))))
