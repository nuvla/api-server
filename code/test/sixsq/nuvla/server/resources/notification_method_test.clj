(ns sixsq.nuvla.server.resources.notification-method-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.notification-method :as t]
    [sixsq.nuvla.server.resources.subscription :as subs])
  (:import
    [java.util UUID]))


(use-fixtures :once ltu/with-test-server-kafka-fixture)

(def base-uri (str p/service-context t/resource-type))

(def valid-notif-conf {:method      "email"
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
                 :body (json/write-str valid-notif-conf))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (let [user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-notif-conf))
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
                     :body (json/write-str updated))
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

      ;; user can delete the data-set
      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))

(deftest lifecycle-with-subscription
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        ; create notification configuration method
        notif-uri (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-notif-conf))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))

        notif-abs-uri (str p/service-context notif-uri)

        subscription {:enabled         true
                      :category        "notification"
                      :parent          (str "subscription-config/" (str (UUID/randomUUID)))
                      :method-id       notif-uri
                      :resource-kind   "nuvlabox-state"
                      :resource-filter "tags='foo'"
                      :resource-id     (str "nuvlabox-status/" (str (UUID/randomUUID)))
                      :criteria        {:kind      "numeric"
                                        :metric    "load"
                                        :value     "75"
                                        :condition ">"}
                      :acl             {:owners ["user/jane"]}}
        subs-uri (-> session-user
                     (request (str p/service-context subs/resource-type)
                              :request-method :post
                              :body (json/write-str subscription))
                     (ltu/body->edn)
                     (ltu/is-status 201)
                     (ltu/location))
        subs-abs-uri (str p/service-context subs-uri)]

    ; can't delete because of subscription referencing the notification method
    (-> session-user
        (request notif-abs-uri :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 409))

    ; delete subscription
    (-> session-user
        (request subs-abs-uri :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))

    ; now can delete notification method
    (-> session-user
        (request notif-abs-uri :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))))