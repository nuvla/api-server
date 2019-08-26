(ns sixsq.nuvla.server.resources.infrastructure-service-group-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as t]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def service-base-uri (str p/service-context service/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  (let [session-anon         (-> (ltu/ring-app)
                                 session
                                 (content-type "application/json"))
        session-admin        (header session-anon authn-info-header
                                     "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user         (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        valid-service        {:acl      valid-acl
                              :subtype  "docker"
                              :endpoint "https://docker.example.org/api"
                              :state    "STARTED"}

        valid-service-create {:name        "my-service"
                              :description "my-description"
                              :tags        ["alpha" "beta" "gamma"]
                              :template    (merge {:href (str infra-service-tpl/resource-type "/"
                                                              infra-service-tpl-generic/method)}
                                                  valid-service)}

        service-group-name   "my-service-group"
        valid-service-group  {:name          service-group-name
                              :description   "my-description"
                              :documentation "http://my-documentation.org"}]

    ;; admin query succeeds but is empty
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; user query succeeds but is empty
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-service-group))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (doseq [session [session-admin session-user]]
      (let [uri           (-> session
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str valid-service-group))
                              (ltu/body->edn)
                              (ltu/is-status 201)
                              (ltu/location))
            abs-uri       (str p/service-context uri)

            ;; verify contents
            service-group (-> session
                              (request abs-uri)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/is-operation-present :edit)
                              (ltu/is-operation-present :delete)
                              (ltu/body))]

        (is (= service-group-name (:name service-group)))
        (is (= "http://my-documentation.org" (:documentation service-group)))
        (is (vector? (:infrastructure-services service-group)))
        (is (zero? (count (:infrastructure-services service-group))))

        ;; creating infrastructure-services that have a parent attribute referencing the service-group
        ;; should show up automatically in the service-group
        (let [service-ids           (set (for [_ (range 3)]
                                           (-> session
                                               (request service-base-uri
                                                        :request-method :post
                                                        :body (json/write-str (-> valid-service-create
                                                                                  (assoc-in [:template :parent] uri)
                                                                                  (assoc :acl {:owners ["user/jane"]}))))
                                               (ltu/body->edn)
                                               (ltu/is-status 201)
                                               (ltu/body)
                                               :resource-id)))

              updated-service-group (-> session
                                        (request abs-uri)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-operation-present :edit)
                                        (ltu/is-operation-present :delete)
                                        (ltu/body))

              service-hrefs         (->> updated-service-group
                                         :infrastructure-services
                                         (map :href)
                                         set)]

          (is (vector? (:infrastructure-services updated-service-group)))
          (is (= service-ids service-hrefs))

          ;; service-group with linked infrastructure-services cannot be deleted
          (-> session
              (request abs-uri :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 409))

          ;; remove the infrastructure-services
          (doseq [service-id service-ids]
            (-> session
                (request (str p/service-context service-id)
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200)))

          ;; verify that the infrastructure-services are gone
          (doseq [service-id service-ids]
            (-> session
                (request (str p/service-context service-id))
                (ltu/body->edn)
                (ltu/is-status 404)))

          (ltu/refresh-es-indices)

          ;; now service-group can be deleted
          (-> session
              (request abs-uri :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200)))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
