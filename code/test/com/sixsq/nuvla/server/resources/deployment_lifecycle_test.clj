(ns com.sixsq.nuvla.server.resources.deployment-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.resource-creation :as resource-creation]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.deployment :as t]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module-application :as module-application]
    [com.sixsq.nuvla.server.resources.module-lifecycle-test :refer [create-parent-projects]]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def module-base-uri (str p/service-context module/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(def timestamp "1964-08-25T10:00:00.00Z")

(defn- setup-module
  [session-owner module-data]
  (let [_         (create-parent-projects (:path module-data) session-owner)
        module-id (-> session-owner
                      (request module-base-uri
                               :request-method :post
                               :body (json/write-str
                                       module-data))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))]
    module-id))

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

    :compatibility             "docker-compose"

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
          module-id          (setup-module session-user (valid-module subtype valid-module-content))
          valid-deployment   {:module {:href module-id}}
          invalid-deployment {:module {:href "module/doesnt-exist"}}
          new-logo-url       "change-logo.url"
          authn-info-jane    {:user-id      "user/jane"
                              :active-claim "user/jane"
                              :claims       ["group/nuvla-anon" "user/jane" "group/nuvla-user" session-id]}
          event-owners-jane  ["group/nuvla-admin" "user/jane"]]

      (testing "admin/user query succeeds but is empty"
        (doseq [session [session-admin session-user]]
          (-> session
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count zero?)
              (ltu/is-operation-present :add)
              (ltu/is-operation-absent :delete)
              (ltu/is-operation-absent :edit))))

      (testing "anon query fails"
        (-> session-anon
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (testing "anon create must fail"
        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str valid-deployment))
            (ltu/body->edn)
            (ltu/is-status 403)))

      (testing "create should fail when user can't view parent credential"
        (-> session-user
            (request base-uri
                     :request-method :post
                     :body (json/write-str (assoc valid-deployment
                                             :parent "credential/x")))
            (ltu/body->edn)
            (ltu/is-status 404)))

      (let [deployment-id  (testing "check deployment creation"
                             (-> session-user
                                 (request base-uri
                                          :request-method :post
                                          :body (json/write-str valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location)))

            deployment-url (str p/service-context deployment-id)]

        (ltu/is-last-event deployment-id
                           {:name               "deployment.add"
                            :description        (str "user/jane added deployment " deployment-id)
                            :category           "add"
                            :success            true
                            :linked-identifiers []
                            :authn-info         authn-info-jane
                            :acl                {:owners event-owners-jane}})

        (testing "admin/user should see one deployment"
          (doseq [session [session-user session-admin]]
            (-> session
                (request base-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-resource-uri t/collection-type)
                (ltu/is-count 1))))

        (let [deployment-response (-> session-user
                                      (request deployment-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :edit)
                                      (ltu/is-operation-present :delete)
                                      (ltu/is-operation-present :start)
                                      (ltu/is-operation-present :clone)
                                      (ltu/is-operation-present :check-dct)
                                      (ltu/is-key-value :execution-mode nil)
                                      (ltu/is-key-value :state "CREATED")
                                      (ltu/is-key-value :owner "user/jane"))

              start-url           (ltu/get-op-url deployment-response "start")

              check-dct-url       (ltu/get-op-url deployment-response "check-dct")]

          (testing "user can't change deployment owner"
            (-> session-user
                (request deployment-url
                         :request-method :put
                         :body (json/write-str {:owner "user/tarzan"
                                                :acl   {:owners ["user/tarzan"]}}))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :owner "user/jane")
                (ltu/is-key-value :owners :acl ["user/jane" "user/tarzan"])))


          (ltu/is-last-event deployment-id
                             {:name               "deployment.edit"
                              :description        (str "user/jane edited deployment " deployment-id)
                              :category           "edit"
                              :success            true
                              :linked-identifiers []
                              :authn-info         authn-info-jane
                              :acl                {:owners (conj event-owners-jane deployment-id "user/tarzan")}})

          (testing "user should not be able to change parent credential to something inaccessible"
            (-> session-user
                (request deployment-url
                         :request-method :put
                         :body (json/write-str {:parent (resource-creation/create-credential-swarm
                                                          session-admin
                                                          {})}))
                (ltu/body->edn)
                (ltu/is-status 403))
            (ltu/is-last-event deployment-id
                               {:name               "deployment.edit"
                                :description        "deployment.edit attempt failed"
                                :category           "edit"
                                :success            false
                                :linked-identifiers []
                                :authn-info         authn-info-jane
                                :acl                {:owners (conj event-owners-jane deployment-id "user/tarzan")}}))
          (let [cred-name     "name credential y"
                credential-id (resource-creation/create-credential-swarm session-user {:name cred-name})]
            (testing "user should be able to change parent credential to something accessible"
              (-> session-user
                  (request deployment-url
                           :request-method :put
                           :body (json/write-str {:parent credential-id}))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :parent credential-id)
                  (ltu/is-key-value :credential-name cred-name))

              (testing "deployment credential should be able to edit deployment even without access to parent credential"
                (let [session-deployment (header session-anon authn-info-header
                                                 (str deployment-id " " deployment-id " group/nuvla-user group/nuvla-anon"))]
                  (-> session-deployment
                      (request deployment-url
                               :request-method :put
                               :body (json/write-str {:description "change by deployment session"}))
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-key-value :description "change by deployment session")
                      (ltu/is-key-value :parent credential-id)
                      (ltu/is-key-value :credential-name cred-name))))))

          (testing "attempt to start the deployment and check the start job was created"
            (let [job-id  (-> session-user
                              (request start-url
                                       :request-method :post)
                              (ltu/body->edn)
                              (ltu/is-status 202)
                              (ltu/location))
                  job-url (ltu/href->url job-id)]
              (ltu/is-last-event deployment-id
                                 {:name               "deployment.start"
                                  :description        "user/jane started deployment"
                                  :category           "action"
                                  :success            true
                                  :linked-identifiers [job-id]
                                  :authn-info         authn-info-jane
                                  :acl                {:owners (conj event-owners-jane deployment-id "user/tarzan")}})
              (-> session-user
                  (request job-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :state "QUEUED")
                  (ltu/is-key-value :action "start_deployment")
                  (ltu/is-operation-absent :get-context))


              (testing "start job get-context action can be called by admin"
                (let [job-get-context-op (-> session-admin
                                             (request job-url)
                                             (ltu/body->edn)
                                             (ltu/is-status 200)
                                             (ltu/is-operation-present :get-context)
                                             (ltu/get-op-url :get-context))]
                  (-> session-admin
                      (request job-get-context-op)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-key-value map? (keyword deployment-id) true))))))

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

            (testing "verify that the credential has the correct metadata"
              (is (:name credential))
              (is (:description credential))
              (is (= deployment-id (:parent credential))))

            (let [module (-> session-user
                             (request (str p/service-context module-id))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             :response :body)]

              (-> session-user
                  (request (str p/service-context module-id)
                           :request-method :put
                           :body (-> module
                                     (assoc-in [:content :environmental-variables]
                                               [{:name "ALPHA_ENV", :value "NOK"}
                                                {:name "NEW", :value "new"}
                                                {:name        "BETA_ENV",
                                                 :description "beta-env variable",
                                                 :required    true}])
                                     (assoc :logo-url new-logo-url)
                                     (assoc-in [:content :commit] "changed logo and add params")
                                     json/write-str))
                  (ltu/body->edn)
                  (ltu/is-status 200)))

            (testing "attempt to queue dct_check job"
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
                    (ltu/is-key-value :action "dct_check"))))

            (let [deployment-response (testing "verify that the state has changed"
                                        (-> session-user
                                            (request deployment-url)
                                            (ltu/body->edn)
                                            (ltu/is-status 200)
                                            (ltu/is-operation-present :edit)
                                            (ltu/is-operation-present :stop)
                                            (ltu/is-operation-absent :delete)
                                            (ltu/is-operation-absent :start)
                                            (ltu/is-key-value :state "STARTING")))

                  stop-url            (ltu/get-op-url deployment-response "stop")]

              (testing "the deployment would be set to \"STARTED\" via the job
              for the tests, set this manually to continue with the workflow"
                (-> session-admin
                    (request deployment-url
                             :request-method :put
                             :body (json/write-str {:state "STARTED"}))
                    (ltu/body->edn)
                    (ltu/is-status 200)))

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

                (testing "update deployment"
                  (-> session-user
                      (request deployment-url
                               :request-method :put
                               :body (json/write-str {:module {:href (str module-id "_1")}}))
                      (ltu/body->edn)
                      (ltu/is-status 200))
                  (let [job-url (testing "try to update the deployment and check
                   the update job was created"
                                  (-> session-user
                                      (request update-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 202)
                                      (ltu/location-url)))]
                    (-> session-user
                        (request job-url)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-key-value :state "QUEUED")
                        (ltu/is-key-value :action "update_deployment"))

                    (testing "state should be updating and logo have been updated"
                      (-> session-user
                          (request deployment-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-key-value :state "UPDATING")
                          (ltu/is-key-value :logo-url :module new-logo-url)))

                    (testing "on success, the deployment update job would set
                    the deployment state to \"STARTED\" for the tests, set this
                    manually to continue with the workflow"
                      (-> session-admin
                          (request deployment-url
                                   :request-method :put
                                   :body (json/write-str {:state "STARTED"}))
                          (ltu/body->edn)
                          (ltu/is-status 200)))))

                (let [log-url (testing "create-log operation"
                                (-> session-user
                                    (request create-log-url
                                             :request-method :post
                                             :body (json/write-str
                                                     {:components ["my-service"]}))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/location-url)))]

                  (testing "verify that the log resource exists"
                    (-> session-user
                        (request log-url)
                        (ltu/body->edn)
                        (ltu/is-status 200)))


                  (let [dep-param-url (testing "normally the start job would
                  create the deployment parameters create one manually to verify
                   later that it is removed with the deployment"
                                        (-> session-admin
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
                                            (ltu/location-url)))]

                    (testing "verify that the deployment parameter was created"
                      (-> session-user
                          (request dep-param-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)))

                    (testing "try to stop the deployment and check the stop job was created"
                      (let [job-id  (-> session-user
                                        (request stop-url
                                                 :request-method :post)
                                        (ltu/body->edn)
                                        (ltu/is-status 202)
                                        (ltu/location))
                            job-url (ltu/href->url job-id)]
                        (ltu/is-last-event deployment-id
                                           {:name               "deployment.stop"
                                            :description        "user/jane stopped deployment"
                                            :category           "action"
                                            :success            true
                                            :linked-identifiers [job-id]
                                            :authn-info         authn-info-jane
                                            :acl                {:owners (conj event-owners-jane deployment-id "user/tarzan")}})
                        (-> session-user
                            (request job-url
                                     :request-method :get)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-key-value :state "QUEUED")
                            (ltu/is-key-value :action "stop_deployment"))))

                    (testing "verify that the state has been updated"
                      (-> session-user
                          (request deployment-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-key-value :state "STOPPING")
                          (ltu/is-operation-absent "start")))

                    (testing "the deployment would be set to \"STOPPED\" via the
                     job for the tests, set this manually to continue with the
                     workflow"
                      (-> session-admin
                          (request deployment-url
                                   :request-method :put
                                   :body (json/write-str {:state "STOPPED"}))
                          (ltu/body->edn)
                          (ltu/is-status 200)))

                    (testing "stopped deployment can be started again"
                      (-> session-user
                          (request deployment-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present "start")))

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

                    (testing "verify user can create another deployment from existing one by using clone action"
                      (let [cloned-dep-id   (-> session-user
                                                (request (str deployment-url "/clone")
                                                         :request-method :post
                                                         :body (json/write-str {:deployment {:href deployment-id}}))
                                                (ltu/body->edn)
                                                (ltu/is-status 201)
                                                ltu/location)
                            cloned-dep-url  (ltu/href->url cloned-dep-id)
                            cloned-stop-url (-> session-user
                                                (request cloned-dep-url
                                                         :request-method :put
                                                         :body (json/write-str {:state "STARTED"}))
                                                (ltu/body->edn)
                                                (ltu/is-status 200)
                                                (ltu/is-operation-present :stop)
                                                (ltu/get-op-url :stop))]
                        (ltu/is-last-event deployment-id
                                           {:name               "deployment.clone"
                                            :description        "user/jane cloned deployment"
                                            :category           "action"
                                            :success            true
                                            :linked-identifiers [cloned-dep-id]
                                            :authn-info         authn-info-jane
                                            :acl                {:owners (conj event-owners-jane deployment-id "user/tarzan")}})
                        (testing "try to stop the cloned deployment with delete option"
                          (let [job-url (-> session-user
                                            (request cloned-stop-url
                                                     :request-method :post
                                                     :body (json/write-str {:delete true}))
                                            (ltu/body->edn)
                                            (ltu/is-status 202)
                                            (ltu/location-url))]
                            (-> session-user
                                (request job-url)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/is-key-value :state "QUEUED")
                                (ltu/is-key-value :action "stop_deployment")
                                (ltu/is-key-value :payload "{\"delete\":true}"))
                            (testing "When job is failed deployment should not be deleted"
                              (-> session-admin
                                  (request job-url
                                           :request-method :put
                                           :body (json/write-str {:state "FAILED"}))
                                  (ltu/body->edn)
                                  (ltu/is-status 200))
                              (-> session-user
                                  (request cloned-dep-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)))
                            (testing "When job is success deployment should be deleted"
                              (-> session-user
                                  (request cloned-dep-url
                                           :request-method :put
                                           :body (json/write-str {:state "STARTED"}))
                                  (ltu/body->edn)
                                  (ltu/is-status 200))
                              (let [job-url (-> session-user
                                                (request cloned-stop-url
                                                         :request-method :post
                                                         :body (json/write-str {:delete true}))
                                                (ltu/body->edn)
                                                (ltu/is-status 202)
                                                (ltu/location-url))]
                                (-> session-admin
                                    (request job-url
                                             :request-method :put
                                             :body (json/write-str {:state "SUCCESS"}))
                                    (ltu/body->edn)
                                    (ltu/is-status 200))
                                (-> session-user
                                    (request cloned-dep-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 404))))))))

                    (testing "verify that the user can delete the deployment"
                      (-> session-user
                          (request deployment-url
                                   :request-method :delete)
                          (ltu/body->edn)
                          (ltu/is-status 200)))

                    (testing "verify that the deployment has disappeared"
                      (-> session-user
                          (request deployment-url)
                          (ltu/body->edn)
                          (ltu/is-status 404)))

                    (testing "verify that the associated credential has also been removed"
                      (-> session-user
                          (request credential-url)
                          (ltu/body->edn)
                          (ltu/is-status 404)))

                    (testing "verify that the deployment parameter has disappeared"
                      (-> session-user
                          (request dep-param-url)
                          (ltu/body->edn)
                          (ltu/is-status 404)))

                    (testing "verify that the deployment log has disappeared"
                      (-> session-user
                          (request log-url)
                          (ltu/body->edn)
                          (ltu/is-status 404))))))))

          (-> session-user
              (request base-uri
                       :request-method :post
                       :body (json/write-str invalid-deployment))
              (ltu/body->edn)
              (ltu/is-status 404)
              (ltu/message-matches "module/doesnt-exist not found"))
          ))

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :delete)
          (ltu/is-status 200)))))


(deftest lifecycle-deployment-component
  (lifecycle-deployment "component" valid-component))


(deftest lifecycle-application
  (let [valid-module-content {:id             (str module-application/resource-type
                                                   "/module-application-uuid")
                              :resource-type  module-application/resource-type
                              :created        timestamp
                              :updated        timestamp
                              :acl            valid-acl

                              :author         "someone"
                              :commit         "wip"

                              :docker-compose "version: \"3.3\"\nservices:\n  web:\n    ..."}
        subtype              "application"]
    (lifecycle-deployment subtype valid-module-content)))


(deftest regression-test-issue-tasklist-2908
  (testing "Edit swarm files content"
    (binding [config-nuvla/*stripe-api-key* nil]
      (let [session-anon         (-> (ltu/ring-app)
                                     session
                                     (content-type "application/json"))
            session-user         (header session-anon authn-info-header
                                         (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
            files                [{:file-name "file1.yaml", :file-content "file1 content"}
                                  {:file-name "file2.yaml", :file-content "file2 content"}]
            valid-module-content {:id             (str module-application/resource-type
                                                       "/module-application-uuid")
                                  :resource-type  module-application/resource-type
                                  :created        timestamp
                                  :updated        timestamp
                                  :acl            valid-acl

                                  :author         "someone"
                                  :commit         "wip"

                                  :files          files
                                  :docker-compose "version: \"3.3\"\nservices:\n  web:\n    ..."}
            subtype              "application"
            module-id            (setup-module session-user
                                               (assoc (valid-module subtype valid-module-content)
                                                 :compatibility "swarm"))
            valid-deployment     {:module {:href module-id}}
            deployment-id        (-> session-user
                                     (request base-uri
                                              :request-method :post
                                              :body (json/write-str valid-deployment))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/location))
            deployment-url       (str p/service-context deployment-id)
            deployment           (-> session-user
                                     (request deployment-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/body))
            deployment           (-> session-user
                                     (request deployment-url
                                              :request-method :put
                                              :body (json/write-str deployment))
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/body))]
        (is (= files (get-in deployment [:module :content :files])))
        (-> session-user
            (request (str p/service-context module-id)
                     :request-method :delete)
            (ltu/is-status 200))))))


(deftest lifecycle-error
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-admin    (header session-anon authn-info-header
                                   "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user     (header session-anon authn-info-header
                                   "user/jane user/jane group/nuvla-user group/nuvla-anon")
          module-id        (setup-module session-user (valid-module "component" valid-component))

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
          module-id        (setup-module session-user (valid-module "component" valid-component))

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
            (request module-url
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
            (ltu/is-status 404)
            (ltu/message-matches (str module-id "_10000 not found")))

        (-> session-user
            (request fetch-url
                     :request-method :put
                     :body (json/write-str {:module-href (str module-id "_1")}))
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

(deftest lifecycle-module-update
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon       (-> (ltu/ring-app)
                                 session
                                 (content-type "application/json"))
          session-user       (header session-anon authn-info-header
                                     "user/jane user/jane group/nuvla-user group/nuvla-anon")
          module-id          (setup-module session-user (valid-module "component" valid-component))

          valid-deployment   {:module {:href module-id}}
          deployment-id      (-> session-user
                                 (request base-uri
                                          :request-method :post
                                          :body (json/write-str valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location))
          deployment-url     (str p/service-context deployment-id)
          module-url         (str p/service-context module-id)
          module             (-> session-user
                                 (request module-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))
          module-content-id  (-> session-user
                                 (request module-url
                                          :request-method :put
                                          :body (json/write-str
                                                  (-> module
                                                      (assoc-in [:content :commit] "changed image version")
                                                      (assoc-in
                                                        [:content :image]
                                                        {:image-name "ubuntu"
                                                         :tag        "18.04"}))))
                                 ltu/body->edn
                                 (ltu/is-status 200)
                                 ltu/body
                                 (get-in [:versions 1 :href]))
          latest-module-href (str module-id "_1")]
      (-> session-user
          (request deployment-url
                   :request-method :put
                   :body (json/write-str {:module {:href latest-module-href}}))
          ltu/body->edn
          (ltu/is-status 200)
          (ltu/is-key-value :href :module latest-module-href)
          (ltu/is-key-value (comp :id :content) :module module-content-id)))))


(deftest lifecycle-bulk-update-force-delete
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon      (-> (ltu/ring-app)
                                session
                                (content-type "application/json"))
          session-user      (header session-anon authn-info-header
                                    "user/jane user/jane group/nuvla-user group/nuvla-anon")

          ;; setup a module that can be referenced from the deployment
          module-id         (setup-module session-user (valid-module "component" valid-component))


          valid-deployment  {:module {:href module-id}}
          deployment-id     (-> session-user
                                (request base-uri
                                         :request-method :post
                                         :body (json/write-str valid-deployment))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location))
          deployment-url    (str p/service-context deployment-id)
          authn-info-jane   {:user-id      "user/jane"
                             :active-claim "user/jane"
                             :claims       ["group/nuvla-anon" "user/jane" "group/nuvla-user"]}
          event-owners-jane ["group/nuvla-admin" "user/jane"]]

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
                        (ltu/message-matches "starting bulk_update_deployment with async job")
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


      (let [job-url (-> session-user
                        (request (str base-uri "/bulk-stop")
                                 :request-method :patch
                                 :headers {:bulk true}
                                 :body (json/write-str
                                         {:filter "id='foobar'"
                                          :other  "hello"}))
                        (ltu/body->edn)
                        (ltu/is-status 202)
                        (ltu/message-matches "starting bulk_stop_deployment with async job")
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

      (let [force-delete-op (-> session-user
                                (request deployment-url)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/is-operation-present :force-delete)
                                (ltu/get-op-url :force-delete))]

        (-> session-user
            (request force-delete-op)
            (ltu/body->edn)
            (ltu/is-status 200))
        (ltu/is-last-event deployment-id
                           {:name       "deployment.force-delete"
                            :category   "action"
                            :success    true
                            :authn-info authn-info-jane
                            :acl        {:owners event-owners-jane}}))

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :delete)
          (ltu/is-status 200)))))


(defn- create-edit-deployment-bulk-edit-tags-lifecycle-test
  [session-owner {depl-name :name
                  depl-tags :tags}]
  (let [;; setup a module that can be referenced from the deployment
        module-id        (setup-module session-owner (valid-module "component" valid-component (str depl-name "/tags" (u/rand-uuid))))

        valid-deployment {:module {:href module-id}}
        deployment-id    (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-deployment))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

        deployment-url   (str p/service-context deployment-id)]
    (-> session-owner
        (request deployment-url
                 :request-method :put
                 :body (json/write-str (cond->
                                         {:name depl-name}
                                         depl-tags (assoc :tags depl-tags))))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :name depl-name)
        (ltu/is-key-value :tags depl-tags))
    deployment-url))


(defn- set-up-bulk-edit-tags-lifecycle-test
  [session-owner deployments]
  (mapv (partial create-edit-deployment-bulk-edit-tags-lifecycle-test session-owner)
        deployments))


(defn- run-bulk-edit-test!
  [{:keys [name endpoint filter tags expected-fn]}]
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
        ne-urls       (set-up-bulk-edit-tags-lifecycle-test
                        session-owner
                        [
                         {:name "NE1"}
                         {:name "NE2" :tags ["foo"]}
                         {:name "NE3" :tags ["foo" "bar"]}])]
    (testing name
      (-> session-owner
          (header "bulk" "yes")
          (request endpoint
                   :request-method :patch
                   :body (json/write-str (cond-> {:doc {:tags tags}}
                                                 filter (assoc :filter filter))))
          (ltu/is-status 200))
      (run!
        (fn [url]
          (let [ne (-> session-owner
                       (request url)
                       (ltu/body->edn))]
            (testing (:name ne)
              (ltu/is-key-value ne :tags (expected-fn (-> ne :response :body))))))
        ne-urls))))

(def endpoint-set-tags (str base-uri "/" "set-tags"))
(def endpoint-add-tags (str base-uri "/" "add-tags"))
(def endpoint-remove-tags (str base-uri "/" "remove-tags"))

(deftest bulk-set-all-tags
  (run-bulk-edit-test! {:endpoint    endpoint-set-tags
                        :test-name   "Set all"
                        :tags        ["baz"]
                        :expected-fn (constantly ["baz"])}))

(deftest bulk-set-tags-on-subset
  (run-bulk-edit-test! {:endpoint    endpoint-set-tags
                        :filter      "(name='NE1') or (name='NE2')"
                        :test-name   "Set just 2"
                        :tags        ["foo" "bar" "baz"]
                        :expected-fn (fn [ne]
                                       (case (:name ne)
                                         "NE3" ["foo" "bar"]
                                         ["foo" "bar" "baz"]))}))

(deftest bulk-remove-all-tags
  (run-bulk-edit-test! {:endpoint    endpoint-set-tags
                        :test-name   "Remove all tags for all deployments"
                        :tags        []
                        :expected-fn (constantly [])}))

(deftest bulk-remove-one-specific-tag
  (run-bulk-edit-test! {:endpoint    endpoint-remove-tags
                        :test-name   "Remove specific tags for all edges"
                        :tags        ["foo"]
                        :expected-fn (fn [ne] (case (:name ne)
                                                "NE3" ["bar"]
                                                []))}))

(deftest bulk-remove-multiple-specific-tags
  (run-bulk-edit-test! {:endpoint    endpoint-remove-tags
                        :test-name   "Remove specific tags for all edges"
                        :tags        ["foo" "bar"]
                        :expected-fn (constantly [])}))

(deftest bulk-add-tags
  (run-bulk-edit-test! {:endpoint    endpoint-add-tags
                        :test-name   "Add specific tags to current tags for all deployments"
                        :tags        ["bar" "baz"]
                        :expected-fn (fn [ne]
                                       (case (:name ne)
                                         "NE1" ["bar" "baz"]
                                         ["foo" "bar" "baz"]))}))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))


(deftest lifecycle-cancel-actions
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
          module-id        (setup-module session-user (valid-module "component" valid-component))
          valid-deployment {:module {:href module-id}}]

      (testing "Canceling start_deployment action"

        ;; attempt to start the deployment and check the start job was created
        (let [deployment-id        (-> session-user
                                       (request base-uri
                                                :request-method :post
                                                :body (json/write-str valid-deployment))
                                       (ltu/body->edn)
                                       (ltu/is-status 201)
                                       (ltu/location))

              deployment-url       (str p/service-context deployment-id)
              deployment-response  (-> session-user
                                       (request deployment-url)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/is-operation-present :start))
              start-url            (ltu/get-op-url deployment-response "start")
              job-url              (-> session-user
                                       (request start-url
                                                :request-method :post)
                                       (ltu/body->edn)
                                       (ltu/is-status 202)
                                       (ltu/location-url))
              cancel-start-job-url (-> session-user
                                       (request job-url)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/is-key-value :state "QUEUED")
                                       (ltu/is-key-value :action "start_deployment")
                                       (ltu/is-operation-present :cancel)
                                       (ltu/get-op-url "cancel"))]
          ;; cancel the start_deployment job
          (-> session-user
              (request cancel-start-job-url)
              (ltu/body->edn)
              (ltu/is-status 200))
          ;; the deployment should go in ERROR state
          (-> session-user
              (request deployment-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "ERROR"))))

      (testing "Canceling update_deployment action"
        (let [deployment-id         (-> session-user
                                        (request base-uri
                                                 :request-method :post
                                                 :body (json/write-str valid-deployment))
                                        (ltu/body->edn)
                                        (ltu/is-status 201)
                                        (ltu/location))

              deployment-url        (str p/service-context deployment-id)
              start-url             (-> session-user
                                        (request deployment-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-operation-present :start)
                                        (ltu/get-op-url "start"))
              _                     (-> session-user
                                        (request start-url
                                                 :request-method :post)
                                        (ltu/body->edn)
                                        (ltu/is-status 202))
              ;; the deployment would be set to "STARTED" via the job
              ;; for the tests, set this manually to continue with the workflow
              _                     (-> session-user
                                        (request deployment-url
                                                 :request-method :put
                                                 :body (json/write-str {:state "STARTED"}))
                                        (ltu/body->edn)
                                        (ltu/is-status 200))
              update-url            (-> session-user
                                        (request deployment-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-operation-present :edit)
                                        (ltu/get-op-url "update"))
              ;; try to update the deployment and check the update job was created
              job-url               (-> session-user
                                        (request update-url
                                                 :request-method :get)
                                        (ltu/body->edn)
                                        (ltu/is-status 202)
                                        (ltu/location-url))
              cancel-update-job-url (-> session-user
                                        (request job-url
                                                 :request-method :get)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-key-value :state "QUEUED")
                                        (ltu/is-key-value :action "update_deployment")
                                        (ltu/is-operation-present :cancel)
                                        (ltu/get-op-url "cancel"))]
          (-> session-user
              (request cancel-update-job-url)
              (ltu/body->edn)
              (ltu/is-status 200))
          ;; the deployment should go in ERROR state
          (-> session-user
              (request deployment-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "ERROR"))))

      (testing "Canceling stop_deployment action"

        (let [deployment-id       (-> session-user
                                      (request base-uri
                                               :request-method :post
                                               :body (json/write-str valid-deployment))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/location))

              deployment-url      (str p/service-context deployment-id)
              start-url           (-> session-user
                                      (request deployment-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :start)
                                      (ltu/get-op-url "start"))
              _                   (-> session-user
                                      (request start-url
                                               :request-method :post)
                                      (ltu/body->edn)
                                      (ltu/is-status 202))
              ;; the deployment would be set to "STARTED" via the job
              ;; for the tests, set this manually to continue with the workflow
              _                   (-> session-user
                                      (request deployment-url
                                               :request-method :put
                                               :body (json/write-str {:state "STARTED"}))
                                      (ltu/body->edn)
                                      (ltu/is-status 200))
              stop-url            (-> session-user
                                      (request deployment-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :stop)
                                      (ltu/get-op-url "stop"))
              ;; try to stop the deployment and check the stop job was created
              job-url             (-> session-user
                                      (request stop-url
                                               :request-method :get)
                                      (ltu/body->edn)
                                      (ltu/is-status 202)
                                      (ltu/location-url))
              cancel-stop-job-url (-> session-user
                                      (request job-url
                                               :request-method :get)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :state "QUEUED")
                                      (ltu/is-key-value :action "stop_deployment")
                                      (ltu/is-operation-present :cancel)
                                      (ltu/get-op-url "cancel"))]
          (-> session-user
              (request cancel-stop-job-url)
              (ltu/body->edn)
              (ltu/is-status 200))
          ;; the deployment should go in ERROR state
          (-> session-user
              (request deployment-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "ERROR")))))))

