(ns sixsq.nuvla.server.resources.service-generic-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.service :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [sixsq.nuvla.server.resources.service-template-generic :as tpl-generic]
    [sixsq.nuvla.server.resources.provider :as provider]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def provider-base-uri (str p/service-context provider/resource-type))


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

        valid-provider {:name          "my-provider"
                        :description   "my-description"
                        :documentation "http://my-documentation.org"}

        provider-id (-> session-user
                        (request provider-base-uri
                                 :request-method :post
                                 :body (json/write-str valid-provider))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))

        service-name "my-service"
        service-desc "my-description"
        service-tags ["alpha" "beta" "gamma"]

        valid-service {:acl        valid-acl
                       :parent     provider-id
                       ;:method     tpl-generic/method
                       :type       "docker"
                       :endpoint   "https://docker.example.org/api"
                       :accessible true}

        valid-create {:name        service-name
                      :description service-desc
                      :tags        service-tags
                      :template    (merge {:href "service-template/generic"}
                                          valid-service)}]

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
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; check creation
    (doseq [session [session-admin session-user]]
      (let [uri (-> session
                    (request base-uri
                             :request-method :post
                             :body (json/write-str valid-create))
                    (ltu/body->edn)
                    (ltu/is-status 201)
                    (ltu/location))
            abs-uri (str p/service-context uri)]

        ;; verify contents
        (let [service (-> session
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present "edit")
                          (ltu/is-operation-present "delete")
                          :response
                          :body)]

          (is (= service-name (:name service)))
          (is (= service-desc (:description service)))
          (is (= service-tags (:tags service)))
          (is (:type service))
          (is (:endpoint service))
          (is (true? (:accessible service))))

        ;; can delete resource
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
