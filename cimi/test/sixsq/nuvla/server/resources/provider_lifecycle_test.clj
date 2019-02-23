(ns sixsq.nuvla.server.resources.provider-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.provider :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.service :as service]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def service-base-uri (str p/service-context service/resource-type))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "USER"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")

        valid-service {:acl        valid-acl
                       :type       "docker"
                       :endpoint   "https://docker.example.org/api"
                       :accessible true}

        provider-name "my-provider"
        valid-provider {:name          provider-name
                        :description   "my-description"
                        :documentation "http://my-documentation.org"}]

    ;; admin query succeeds but is empty
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; user query succeeds but is empty
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-provider))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (doseq [session [session-admin session-user]]
      (let [uri (-> session
                    (request base-uri
                             :request-method :post
                             :body (json/write-str valid-provider))
                    (ltu/body->edn)
                    (ltu/is-status 201)
                    (ltu/location))
            abs-uri (str p/service-context uri)]

        ;; verify contents
        (let [provider (-> session
                           (request abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-operation-present "edit")
                           (ltu/is-operation-present "delete")
                           :response
                           :body)]

          (is (= provider-name (:name provider)))
          (is (= "http://my-documentation.org" (:documentation provider)))
          (is (vector? (:services provider)))
          (is (zero? (count (:services provider))))

          ;; creating services that have a parent attribute referencing the provider
          ;; should show up automatically in the provider
          (let [service-ids (set (for [_ (range 3)]
                                   (-> session-user
                                       (request service-base-uri
                                                :request-method :post
                                                :body (json/write-str (assoc valid-service :parent uri)))
                                       (ltu/body->edn)
                                       (ltu/is-status 201)
                                       :response
                                       :body
                                       :resource-id)))

                updated-provider (-> session
                                     (request abs-uri)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present "edit")
                                     (ltu/is-operation-present "delete")
                                     :response
                                     :body)

                service-hrefs (->> updated-provider
                                   :services
                                   (map :href)
                                   set)]

            (is (vector? (:services updated-provider)))
            (is (= service-ids service-hrefs))))

        ;; provider can be deleted
        ;; FIXME: Should be able to be deleted ONLY when there are no associated services!
        (-> session
            (request abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
