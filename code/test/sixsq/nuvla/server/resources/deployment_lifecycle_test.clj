(ns sixsq.nuvla.server.resources.deployment-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
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


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def module-base-uri (str p/service-context module/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(def timestamp "1964-08-25T10:00:00.00Z")


(defn valid-module
  ([subtype content]
   (valid-module subtype content "a/b/c"))
  ([subtype content path]
   {:id                        (str module/resource-type "/component-uuid")
    :resource-type             module/resource-type
    :created                   timestamp
    :updated                   timestamp
    :parent-path               (str/join "/" (butlast (str/split path #"/")))
    :path                      path
    :subtype                   subtype

    :logo-url                  "https://example.org/logo"

    :data-accept-content-types ["application/json" "application/x-something"]
    :data-access-protocols     ["http+s3" "posix+nfs"]

    :content                   content}))


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
                                     (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
          session-user       (header session-anon authn-info-header
                                     (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))

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
                                      (ltu/is-operation-present :check-dct)
                                      (ltu/is-key-value :state "CREATED")
                                      (ltu/is-key-value :owner "user/jane"))

              start-url           (ltu/get-op-url deployment-response "start")

              check-dct-url       (ltu/get-op-url deployment-response "check-dct")]

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

          ;; attempt to start the deployment and check the start job was created
          (let [job-url (-> session-user
                            (request start-url
                                     :request-method :post)
                            (ltu/body->edn)
                            (ltu/is-status 202)
                            (ltu/location-url))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :state "QUEUED")
                (ltu/is-key-value :action "start_deployment")
                (ltu/is-operation-absent :get-context))


            (-> session-admin
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-present :get-context)))

          (let [deployment     (-> session-user
                                   (request deployment-url
                                            :request-method :put
                                            :body (json/write-str {:owner "user/tarzan"
                                                                   :acl   {:owners ["user/tarzan"]}}))
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/is-key-value some? :api-credentials true)
                                   (ltu/body))
                credential-id  (-> deployment :api-credentials :api-key)
                credential-url (str p/service-context credential-id)
                credential     (-> session-admin
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

            ;; attempt to queue dct_check job
            (let [job-url (-> session-user
                              (request check-dct-url
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
                  (ltu/is-key-value :action "dct_check")))

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
              (-> session-admin
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
                  (-> session-admin
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
                  (let [dep-param-url (-> session-admin
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
                        (request dep-param-url)
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
                        (ltu/is-operation-absent "start"))

                    ;; the deployment would be set to "STOPPED" via the job
                    ;; for the tests, set this manually to continue with the workflow
                    (-> session-admin
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

                    ;; Disabled test because of flapping error build
                    ;; on edit changes on deployment acl are propagated to deployment parameters
                    #_(let [deployment-acl (-> session-user
                                               (request deployment-url)
                                               (ltu/body->edn)
                                               (ltu/is-status 200)
                                               (ltu/body)
                                               :acl)]

                        (ltu/refresh-es-indices)

                        (-> session-user
                            (request dep-param-url)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-key-value #(= deployment-acl %) :acl true))

                        (-> session-user
                            (request deployment-url
                                     :request-method :put
                                     :body (json/write-str
                                             {:acl (update deployment-acl
                                                           :view-data conj "user/shared")}))
                            (ltu/body->edn)
                            (ltu/is-status 200))

                        (-> session-user
                            (request dep-param-url)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-key-value #(some #{"user/shared"} (:view-data %))
                                              :acl "user/shared")))

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
                        (request dep-param-url)
                        (ltu/body->edn)
                        (ltu/is-status 404))

                    ;; verify that the deployment log has disappeared
                    (-> session-user
                        (request log-url)
                        (ltu/body->edn)
                        (ltu/is-status 404)))))))

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


(deftest lifecycle-error
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-admin    (header session-anon authn-info-header
                                   "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user     (header session-anon authn-info-header
                                   "user/jane user/jane group/nuvla-user group/nuvla-anon")

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
        (-> session-admin
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

(deftest lifecycle-fetch-module
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   "user/jane user/jane group/nuvla-user group/nuvla-anon")

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

            deployment-url (str p/service-context deployment-id)
            fetch-url      (-> session-user
                               (request deployment-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url :fetch-module))
            module-url     (str p/service-context module-id)
            module         (-> session-user
                               (request module-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

        (-> session-user
            (request (str p/service-context module-id)
                     :request-method :put
                     :body (json/write-str
                             (assoc-in module
                                       [:content :image]
                                       {:image-name "ubuntu"
                                        :tag        "18.04"})))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request fetch-url)
            (ltu/body->edn)
            (ltu/is-status 400)
            (ltu/message-matches "invalid module-href"))

        ;; try resolve module version not existing should fail
        (-> session-user
            (request fetch-url
                     :request-method :put
                     :body (json/write-str {:module-href (str module-id "_10000")}))
            (ltu/body->edn)
            (ltu/is-status 400)
            (ltu/message-matches "cannot resolve"))

        (-> session-user
            (request fetch-url
                     :request-method :put
                     :body (json/write-str {:module-href module-id}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value #(-> % :content :image :tag) :module "18.04"))

        (-> session-user
            (request deployment-url
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200)))

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :delete)
          (ltu/is-status 200)))))


(deftest lifecycle-bulk-update
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   "user/jane user/jane group/nuvla-user group/nuvla-anon")

          ;; setup a module that can be referenced from the deployment
          module-id        (-> session-user
                               (request module-base-uri
                                        :request-method :post
                                        :body (json/write-str
                                                (valid-module "component" valid-component)))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))

          valid-deployment {:module {:href module-id}}
          deployment-id    (-> session-user
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str valid-deployment))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))

          deployment-url   (str p/service-context deployment-id)]

      ;; check deployment creation
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      (-> session-user
          (request (str base-uri "/foo")
                   :request-method :patch)
          (ltu/body->edn)
          (ltu/is-status 404)
          (ltu/message-matches #"undefined action \(patch, \[\"deployment\" \"foo\".*"))

      (-> session-user
          (request (str base-uri "/bulk-update")
                   :request-method :patch)
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Bulk request should contain bulk http header."))

      (-> session-user
          (request (str base-uri "/bulk-update")
                   :request-method :patch
                   :headers {:bulk true}
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Bulk request should contain a non empty cimi filter."))

      (-> session-user
          (request (str base-uri "/bulk-update")
                   :request-method :patch
                   :headers {:bulk true}
                   :body (json/write-str
                           {:filter "foobar"
                            :other  "hello"}))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches "Invalid CIMI filter. Parse error at line"))


      (let [job-url (-> session-user
                        (request (str base-uri "/bulk-update")
                                 :request-method :patch
                                 :headers {:bulk true}
                                 :body (json/write-str
                                         {:filter "id='foobar'"
                                          :other  "hello"}))
                        (ltu/body->edn)
                        (ltu/is-status 202)
                        (ltu/message-matches "starting bulk-update with async job")
                        (ltu/location-url))]
        (-> session-user
            (request job-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value
              :payload (json/write-str
                         {:filter     "id='foobar'"
                          :other      "hello"
                          :authn-info {:user-id      "user/jane"
                                       :active-claim "user/jane"
                                       :claims       ["group/nuvla-anon"
                                                      "user/jane"
                                                      "group/nuvla-user"]}}))))

      (-> session-user
          (request deployment-url
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :delete)
          (ltu/is-status 200))

      )))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))


;; FIXME: Remove this test after sixsq.nuvla.server.resources.deployment/gnss-expiry-date.
;;        The test will fail after this date.
(deftest gnss-extended-api-key
  (binding [config-nuvla/*stripe-api-key* nil]

    (doseq [g t/gnss-groups]
      (is (t/gnss-group? {:nuvla/authn {:active-claim g}} t/gnss-expiry-date)))
    (doseq [g t/gnss-groups]
      (is (not (t/gnss-group? {:nuvla/authn {:active-claim g}} "1970-01-01T00:00:00.000Z"))))
    (is (not (t/gnss-group? {:nuvla/authn {:active-claim "group/hello"}} t/gnss-expiry-date)))
    (is (not (t/gnss-group? {:nuvla/authn {:active-claim "group/hello"}} "1970-01-01T00:00:00.000Z")))

    (doseq [gg t/gnss-groups]
      (let [session-anon        (-> (ltu/ring-app)
                                    session
                                    (content-type "application/json"))
            session-admin       (header session-anon authn-info-header
                                        "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
            session-user        (header session-anon authn-info-header
                                        (str gg " " gg " group/nuvla-user group/nuvla-anon"))

            ;; setup a module that can be referenced from the deployment
            module-id           (-> session-user
                                    (request module-base-uri
                                             :request-method :post
                                             :body (json/write-str
                                                     (valid-module "component" valid-component (format "a/b/%s" gg))))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/location))

            valid-deployment    {:module {:href module-id}}
            deployment-id       (-> session-user
                                    (request base-uri
                                             :request-method :post
                                             :body (json/write-str valid-deployment))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/location))
            deployment-url      (str p/service-context deployment-id)
            deployment-response (-> session-user
                                    (request deployment-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-key-value :state "CREATED")
                                    (ltu/is-key-value :owner gg))
            start-url           (ltu/get-op-url deployment-response "start")
            job-url             (-> session-user
                                    (request start-url
                                             :request-method :post)
                                    (ltu/body->edn)
                                    (ltu/is-status 202)
                                    (ltu/location-url))
            _                   (-> session-user
                                    (request job-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-key-value :state "QUEUED")
                                    (ltu/is-key-value :action "start_deployment"))

            deployment          (-> session-user
                                    (request deployment-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-key-value some? :api-credentials true)
                                    (ltu/body))
            credential-id       (-> deployment :api-credentials :api-key)
            credential-url      (str p/service-context credential-id)
            credential          (-> session-admin
                                    (request credential-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/body))]

        (is (= deployment-id (:parent credential)))

        ;; verify that the credential has the correct identity
        (is (= gg (-> credential :claims :identity)))))))
