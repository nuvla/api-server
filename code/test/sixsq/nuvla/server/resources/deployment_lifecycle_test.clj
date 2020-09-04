(ns sixsq.nuvla.server.resources.deployment-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def module-base-uri (str p/service-context module/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(def timestamp "1964-08-25T10:00:00.00Z")


(defn valid-module
  [subtype content]
  {:id                        (str module/resource-type "/connector-uuid")
   :resource-type             module/resource-type
   :created                   timestamp
   :updated                   timestamp
   :parent-path               "a/b"
   :path                      "a/b/c"
   :subtype                   subtype

   :logo-url                  "https://example.org/logo"

   :data-accept-content-types ["application/json" "application/x-something"]
   :data-access-protocols     ["http+s3" "posix+nfs"]

   :content                   content})


(def valid-component {:author                  "someone"
                      :commit                  "wip"

                      :architectures           ["amd64" "arm/v6"]
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


(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")



(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(defn lifecycle-deployment
  [subtype valid-module-content]
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon       (-> (ltu/ring-app)
                                 session
                                 (content-type "application/json"))
          session-admin      (header session-anon authn-info-header
                                     (str "group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
          session-user       (header session-anon authn-info-header
                                     (str "user/jane group/nuvla-user group/nuvla-anon " session-id))

          ;; setup a module that can be referenced from the deployment
          module-id          (-> session-user
                                 (request module-base-uri
                                          :request-method :post
                                          :body (json/write-str
                                                  (valid-module subtype valid-module-content)))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location))

          valid-deployment   {:module {:href module-id}}
          invalid-deployment {:module {:href "module/doesnt-exist"}}]

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
                                      (ltu/is-operation-present :clone)
                                      (ltu/is-operation-present :fetch-module)
                                      (ltu/is-key-value :state "CREATED")
                                      (ltu/is-key-value :owner "user/jane"))

              start-url           (ltu/get-op-url deployment-response "start")

              fetch-module-url    (ltu/get-op-url deployment-response "fetch-module")

              deployment          (ltu/body deployment-response)]

          ;; user can't change deployment owner
          (-> session-user
              (request deployment-url
                       :request-method :put
                       :body (json/write-str {:owner "user/tarzan"
                                              :acl   {:owners ["user/tarzan"]}}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :owner "user/jane")
              (ltu/is-key-value :owners :acl ["user/jane" "user/tarzan"]))

          ;; verify that api key/secret pair was created
          (is (:api-credentials deployment))

          (when-let [credential-id (-> deployment :api-credentials :api-key)]
            (let [credential-url (str p/service-context credential-id)
                  credential     (-> session-user
                                     (request credential-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/body))]

              ;; verify that the credential has the correct metadata
              (is (:name credential))
              (is (:description credential))
              (is (= deployment-id (:parent credential)))

              (let [module (-> session-user
                               (request (str "/api/" module-id))
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               :response :body)]

                (-> session-user
                    (request (str "/api/" module-id)
                             :request-method :put
                             :body (json/write-str (assoc-in module [:content :environmental-variables]
                                                             [{:name "ALPHA_ENV", :value "NOK"}
                                                              {:name "NEW", :value "new"}
                                                              {:name        "BETA_ENV",
                                                               :description "beta-env variable",
                                                               :required    true}]
                                                             )))
                    (ltu/body->edn)
                    (ltu/is-status 200)))

              ;; try call fetch-module
              (-> session-user
                  (request fetch-module-url
                           :request-method :post)
                  (ltu/is-status 200)
                  (ltu/body->edn))

              ;; attempt to start the deployment and check the start job was created
              (let [job-url (-> session-user
                                (request start-url
                                         :request-method :post)
                                (ltu/body->edn)
                                (ltu/is-status 202)
                                (ltu/location-url))]
                (-> session-user
                    (request job-url
                             :request-method :get)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :state "QUEUED")
                    (ltu/is-key-value :action "start_deployment")))

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

                ;; the deployment would be set to "STARTED" via the job
                ;; for the tests, set this manually to continue with the workflow
                (-> session-user
                    (request deployment-url
                             :request-method :put
                             :body (json/write-str {:state "STARTED"}))
                    (ltu/body->edn)
                    (ltu/is-status 200))

                (let [response       (-> session-user
                                         (request deployment-url)
                                         (ltu/body->edn)
                                         (ltu/is-status 200)
                                         (ltu/is-operation-present :edit)
                                         (ltu/is-operation-present :stop)
                                         (ltu/is-operation-present :create-log)
                                         (ltu/is-operation-absent :delete)
                                         (ltu/is-operation-absent :start)
                                         (ltu/is-key-value :state "STARTED"))
                      create-log-url (ltu/get-op-url response "create-log")
                      update-url     (ltu/get-op-url response "update")]


                  ;; update deployment
                  ;; try to update the deployment and check the update job was created
                  (let [job-url (-> session-user
                                    (request update-url
                                             :request-method :get)
                                    (ltu/body->edn)
                                    (ltu/is-status 202)
                                    (ltu/location-url))]
                    (-> session-user
                        (request job-url
                                 :request-method :get)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-key-value :state "QUEUED")
                        (ltu/is-key-value :action "update_deployment"))

                    ;; verify that the state has been updated
                    (-> session-user
                        (request deployment-url)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-key-value :state "UPDATING"))

                    ;; on success, the deployment update job would set the deployment state to "STARTED"
                    ;; for the tests, set this manually to continue with the workflow
                    (-> session-user
                        (request deployment-url
                                 :request-method :put
                                 :body (json/write-str {:state "STARTED"}))
                        (ltu/body->edn)
                        (ltu/is-status 200)))


                  ;; check create-log operation
                  (let [log-url (-> session-user
                                    (request create-log-url
                                             :request-method :post
                                             :body (json/write-str {:service "my-service"}))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/location-url))]

                    ;; verify that the log resource exists
                    (-> session-user
                        (request log-url)
                        (ltu/body->edn)
                        (ltu/is-status 200))

                    ;; normally the start job would create the deployment parameters
                    ;; create one manually to verify later that it is removed with the
                    ;; deployment
                    (let [dp-url (-> session-admin
                                     (request (str p/service-context "deployment-parameter")
                                              :request-method :post
                                              :body (json/write-str
                                                      {:parent  deployment-id
                                                       :name    "test-parameter"
                                                       :node-id "machine"
                                                       :acl     {:owners   ["group/nuvla-admin"]
                                                                 :edit-acl ["user/jane"]}}))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/location-url))]

                      ;; verify that the deployment parameter was created
                      (-> session-user
                          (request dp-url)
                          (ltu/body->edn)
                          (ltu/is-status 200))

                      ;; try to stop the deployment and check the stop job was created
                      (let [job-url (-> session-user
                                        (request stop-url
                                                 :request-method :post)
                                        (ltu/body->edn)
                                        (ltu/is-status 202)
                                        (ltu/location-url))]
                        (-> session-user
                            (request job-url
                                     :request-method :get)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-key-value :state "QUEUED")
                            (ltu/is-key-value :action "stop_deployment")))

                      ;; verify that the state has been updated
                      (-> session-user
                          (request deployment-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-key-value :state "STOPPING")
                          (ltu/is-operation-absent "fetch-module")
                          (ltu/is-operation-absent "start"))

                      ;; the deployment would be set to "STOPPED" via the job
                      ;; for the tests, set this manually to continue with the workflow
                      (-> session-user
                          (request deployment-url
                                   :request-method :put
                                   :body (json/write-str {:state "STOPPED"}))
                          (ltu/body->edn)
                          (ltu/is-status 200))

                      ;; stopped deployment can be started again
                      (-> session-user
                          (request deployment-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present "start"))

                      ;; verify user can create another deployment from existing one
                      (let [deployment-url-from-dep (-> session-user
                                                        (request base-uri
                                                                 :request-method :post
                                                                 :body (json/write-str {:deployment {:href deployment-id}}))
                                                        (ltu/body->edn)
                                                        (ltu/is-status 201)
                                                        (ltu/location-url))]
                        (-> session-user
                            (request deployment-url-from-dep
                                     :request-method :delete)
                            (ltu/body->edn)
                            (ltu/is-status 200)))

                      ;; verify user can create another deployment from existing one by using clone action
                      (let [deployment-url-from-dep (-> session-user
                                                        (request (str deployment-url "/clone")
                                                                 :request-method :post
                                                                 :body (json/write-str {:deployment {:href deployment-id}}))
                                                        (ltu/body->edn)
                                                        (ltu/is-status 201)
                                                        (ltu/location-url))]
                        (-> session-user
                            (request deployment-url-from-dep
                                     :request-method :delete)
                            (ltu/body->edn)
                            (ltu/is-status 200)))

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
                          (ltu/is-status 404))

                      ;; verify that the deployment log has disappeared
                      (-> session-user
                          (request log-url)
                          (ltu/body->edn)
                          (ltu/is-status 404))))))))

          (-> session-user
              (request base-uri
                       :request-method :post
                       :body (json/write-str invalid-deployment))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/message-matches #"cannot resolve.*"))
          ))

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :delete)
          (ltu/is-status 200)))))


(deftest lifecycle-deployment-component
  (lifecycle-deployment "component" valid-component))


(deftest lifecycle-error
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-admin    (header session-anon authn-info-header
                                   "group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user     (header session-anon authn-info-header
                                   "user/jane group/nuvla-user group/nuvla-anon")

          ;; setup a module that can be referenced from the deployment
          module-id        (-> session-user
                               (request module-base-uri
                                        :request-method :post
                                        :body (json/write-str
                                                (valid-module "component" valid-component)))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))

          valid-deployment {:module {:href module-id}}]

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

        ;; the deployment would be set to "ERROR" via a job
        ;; set this manually to continue with the workflow
        (-> session-user
            (request deployment-url
                     :request-method :put
                     :body (json/write-str {:state "ERROR"}))
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
            (ltu/is-status 404)))

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :delete)
          (ltu/is-status 200)))))


(deftest lifecycle-application
  (let [valid-application {:id             (str module-application/resource-type
                                                "/module-application-uuid")
                           :resource-type  module-application/resource-type
                           :created        timestamp
                           :updated        timestamp
                           :acl            valid-acl

                           :author         "someone"
                           :commit         "wip"

                           :docker-compose "version: \"3.3\"\nservices:\n  web:\n    ..."}]
    (lifecycle-deployment "application" valid-application)))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
