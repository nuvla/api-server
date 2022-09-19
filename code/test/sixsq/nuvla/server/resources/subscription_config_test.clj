(ns sixsq.nuvla.server.resources.subscription-config-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.subscription-config :as t])
  (:import
    [java.util UUID]))


(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        valid-subscription-config {:enabled         true
                                   :category        "notification"
                                   :method-ids       [(str "notification-method/" (str (UUID/randomUUID)))]
                                   :resource-kind   "infrastructure-service"
                                   :resource-filter "tags='foo'"
                                   :criteria        {:kind      "numeric"
                                                     :metric    "load"
                                                     :value     "75"
                                                     :condition ">"}
                                   :acl             {:owners ["user/jane"]}}]
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
    (let [subs-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-subscription-config))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

          subs-abs-uri (str p/service-context subs-uri)]

      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      (-> session-admin
          (request subs-abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :enable)
          (ltu/is-operation-present :disable)
          (ltu/is-operation-present :set-notif-method-ids))

      ;; verify that an edit works
      (let [notif-ids [(str "notification-method/" (str (UUID/randomUUID)))]
            updated (assoc valid-subscription-config :method-ids notif-ids)]

        (-> session-user
            (request subs-abs-uri
                     :request-method :put
                     :body (json/write-str updated))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/body))

        (let [updated-body (-> session-admin
                               (request subs-abs-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

          (is (= notif-ids (:method-ids updated-body)))))

      ;;
      ;; disable subscription
      (-> session-user
          (request (str subs-abs-uri "/" t/disable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      (is (= false (-> session-user
                       (request subs-abs-uri)
                       (ltu/body->edn)
                       (ltu/body)
                       :enabled)))

      ;;
      ;; enable subscription
      (-> session-user
          (request (str subs-abs-uri "/" t/enable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      (is (= true (-> session-user
                      (request subs-abs-uri)
                      (ltu/body->edn)
                      (ltu/body)
                      :enabled)))

      ;; set method-ids using operation
      (let [method-ids [(str "notification-method/" (str (UUID/randomUUID)))]]
        (-> session-user
            (request (str subs-abs-uri "/" t/set-notif-method-ids)
                     :request-method :post
                     :body (json/write-str {:method-ids method-ids}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (is (= method-ids (-> session-user
                             (request subs-abs-uri)
                             (ltu/body->edn)
                             (ltu/body)
                             :method-ids))))
      ;; delete
      (-> session-user
          (request subs-abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
