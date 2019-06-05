(ns sixsq.nuvla.server.resources.deployment-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def module-base-uri (str p/service-context module/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-module {:id                        (str module/resource-type "/connector-uuid")
                   :resource-type             module/resource-type
                   :created                   timestamp
                   :updated                   timestamp
                   :parent-path               "a/b"
                   :path                      "a/b/c"
                   :subtype                   "component"

                   :logo-url                  "https://example.org/logo"

                   :data-accept-content-types ["application/json" "application/x-something"]
                   :data-access-protocols     ["http+s3" "posix+nfs"]})


(def valid-module-component {:author                  "someone"
                             :commit                  "wip"

                             :architecture            "x86"
                             :image                   {:image-name "ubuntu"
                                                       :tag        "16.04"}
                             :ports                   [{:protocol       "tcp"
                                                        :target-port    22
                                                        :published-port 8022}]

                             :environmental-variables [{:name  "ALPHA_ENV"
                                                        :value "OK"}
                                                       {:name        "BETA_ENV"
                                                        :description "beta-env variable"
                                                        :required    true}]

                             :output-parameters       [{:name        "alpha"
                                                        :description "my-alpha"}
                                                       {:name        "beta"
                                                        :description "my-beta"}
                                                       {:name        "gamma"
                                                        :description "my-gamma"}]})


(deftest lifecycle
  (let [session-anon     (-> (ltu/ring-app)
                             session
                             (content-type "application/json"))
        session-admin    (header session-anon authn-info-header
                                 "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user     (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        ;; setup a module that can be referenced from the deployment
        module-id        (-> session-user
                             (request module-base-uri
                                      :request-method :post
                                      :body (json/write-str (assoc valid-module :content valid-module-component)))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

        valid-deployment {:module {:href module-id}}]

    ;; admin/user query succeeds but is empty
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
                 :body (json/write-str valid-deployment))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check deployment creation
    (let [deployment-id  (-> session-user
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-deployment))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

          deployment-url (str p/service-context deployment-id)]

      ;; admin/user should see one deployment
      (doseq [session [session-user session-admin]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-resource-uri t/collection-type)
            (ltu/is-count 1)))

      (let [deployment-response (-> session-user
                                    (request deployment-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-operation-present :edit)
                                    (ltu/is-operation-present :delete)
                                    (ltu/is-operation-present :start)
                                    (ltu/is-key-value :state "CREATED"))

            start-url           (ltu/get-op-url deployment-response "start")

            deployment          (ltu/body deployment-response)]

        ;; verify that api key/secret pair was created
        (is (:api-credentials deployment))

        (when-let [credential-id (-> deployment :api-credentials :api-key)]
          (let [credential-url (str p/service-context credential-id)
                credential     (-> session-user
                                   (request credential-url)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   :response
                                   :body)]

            ;; verify that the credential has the correct metadata
            (is (:name credential))
            (is (:description credential))
            (is (= deployment-id (:parent credential)))


            ;; attempt to start the deployment
            (-> session-user
                (request start-url
                         :request-method :post)
                (ltu/body->edn)
                (ltu/is-status 202))

            ;; verify that the state has changed
            (let [deployment-response (-> session-user
                                          (request deployment-url)
                                          (ltu/body->edn)
                                          (ltu/is-status 200)
                                          (ltu/is-operation-present :edit)
                                          (ltu/is-operation-present :stop)
                                          (ltu/is-operation-absent :delete)
                                          (ltu/is-operation-absent :start)
                                          (ltu/is-key-value :state "STARTING"))

                  stop-url            (ltu/get-op-url deployment-response "stop")]

              ;; normally the start job would create the deployment parameters
              ;; create one manually to verify later that it is removed with the
              ;; deployment
              (let [dp-url (-> session-admin
                               (request (str p/service-context "deployment-parameter")
                                        :request-method :post
                                        :body (json/write-str {:name       "test-parameter"
                                                               :node-id    "machine"
                                                               :deployment {:href deployment-id}
                                                               :acl        {:owners   ["group/nuvla-admin"]
                                                                            :edit-acl ["user/jane"]}}))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location-url))]

                ;; verify that the deployment parameter was created
                (-> session-user
                    (request dp-url)
                    (ltu/body->edn)
                    (ltu/is-status 200))

                ;; try to stop the deployment
                (-> session-user
                    (request stop-url
                             :request-method :post)
                    (ltu/body->edn)
                    (ltu/is-status 202))

                ;; verify that the state has been updated
                (-> session-user
                    (request deployment-url)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :state "STOPPING"))

                ;; the deployment would be set to "STOPPED" via the job
                ;; for the tests, set this manually to continue with the workflow
                (-> session-user
                    (request deployment-url
                             :request-method :put
                             :body (json/write-str {:state "STOPPED"}))
                    (ltu/body->edn)
                    (ltu/is-status 200))

                ;; verify that the user can delete the deployment
                (-> session-user
                    (request deployment-url
                             :request-method :delete)
                    (ltu/body->edn)
                    (ltu/is-status 200))

                ;; verify that the deployment has disappeared
                (-> session-user
                    (request deployment-url)
                    (ltu/body->edn)
                    (ltu/is-status 404))

                ;; verify that the associated credential has also been removed
                (-> session-user
                    (request credential-url)
                    (ltu/body->edn)
                    (ltu/is-status 404))

                ;; verify that the deployment parameter has disappeared
                (-> session-user
                    (request dp-url)
                    (ltu/body->edn)
                    (ltu/is-status 404))))))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
