(ns sixsq.nuvla.server.resources.subscription-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.subscription :as t])
  (:import
    [java.util UUID]))


(use-fixtures :once ltu/with-test-server-kafka-fixture)

(def base-uri (str p/service-context t/resource-type))

(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        subs-resource (str "nuvlabox-status/" (str (UUID/randomUUID)))
        valid-subscription {:enabled true
                            :category "notification"
                            :parent (str "subscription-config/" (str (UUID/randomUUID)))
                            :method-ids [(str "notification-method/" (str (UUID/randomUUID)))]
                            :resource-kind "nuvlabox-state"
                            :resource-filter "tags='foo'"
                            :resource-id subs-resource
                            :criteria {:kind "numeric"
                                       :metric "load"
                                       :value "75"
                                       :condition ">"}
                            :acl      {:owners ["user/jane"]}}]
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
                 :body (json/write-str valid-subscription))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (let [user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-subscription))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

          user-abs-uri (str p/service-context user-uri)]

      ;; subscription can be found by :resource
      (-> session-user
          (content-type "application/x-www-form-urlencoded")
          (request base-uri
                   :request-method :put
                   :body (rc/form-encode {:filter (format "resource-id='%s'" subs-resource)}))
          (ltu/body->edn)
          (ltu/is-count 1)
          (ltu/is-status 200))

      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      (let [body (-> session-admin
                     (request user-abs-uri)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/body))]
        (is (= t/resource-type (:name body)))
        (is (= t/resource-type (:description body))))

      ;; verify that an edit works
      (let [notif-ids [(str "notification-method/" (str (UUID/randomUUID)))]
            updated (assoc valid-subscription :method-ids notif-ids)]

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

          (is (= notif-ids (:method-ids updated-body)))))

      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
