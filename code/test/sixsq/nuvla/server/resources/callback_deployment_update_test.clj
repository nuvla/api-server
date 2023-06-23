(ns sixsq.nuvla.server.resources.callback-deployment-update-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-deployment-update :as cdu]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment :as deployment]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.notification :as notif]))


(use-fixtures :once ltu/with-test-server-fixture)

(def module-base-uri (str p/service-context module/resource-type))
(def deployment-base-uri (str p/service-context deployment/resource-type))
(def callback-base-uri (str p/service-context callback/resource-type))

(def timestamp "2000-00-00T00:00:00.00Z")

(def image-name "image")
(def old-image-tag "0.1")
(def new-image-tag "0.2")
(def old-commit-msg (format "initial image %s:%s" image-name old-image-tag))
(def new-commit-msg (format "new image %s:%s" image-name new-image-tag))

(defn valid-module
  [subtype content]
  {:id                        (str module/resource-type "/connector-uuid")
   :resource-type             module/resource-type
   :created                   timestamp
   :updated                   timestamp
   :path                      "a/b"
   :subtype                   subtype

   :logo-url                  "https://example.org/logo"

   :data-accept-content-types ["application/json" "application/x-something"]
   :data-access-protocols     ["http+s3" "posix+nfs"]

   :content                   content})


(def valid-component {:author                  "someone"
                      :commit                  old-commit-msg

                      :architectures           ["amd64" "arm/v6"]
                      :image                   {:image-name image-name
                                                :tag        old-image-tag}
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


(deftest callback-deployment-update-component
  (let [subtype "component"]
    (binding [config-nuvla/*stripe-api-key* nil]
      (let [session-anon     (-> (ltu/ring-app)
                                 session
                                 (content-type "application/json"))
            session-admin    (header session-anon authn-info-header
                                     "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
            session-user     (header session-anon authn-info-header
                                     "user/jane user/jane group/nuvla-user group/nuvla-anon")

            ;; setup a module that can be referenced from the deployment
            _project-module  (-> session-user
                                 (request module-base-uri
                                          :request-method :post
                                          :body (json/write-str
                                                  {:subtype "project"
                                                   :path    "a"}))
                                 (ltu/body->edn)
                                 (ltu/is-status 201))
            module-id        (-> session-user
                                 (request module-base-uri
                                          :request-method :post
                                          :body (json/write-str
                                                  (valid-module subtype valid-component)))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location))

            valid-deployment {:module {:href module-id}}]

        ;; create deployment
        (let [deployment-id  (-> session-user
                                 (request deployment-base-uri
                                          :request-method :post
                                          :body (json/write-str valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location))

              deployment-url (str p/service-context deployment-id)]

          (let [deployment-response (-> session-user
                                        (request deployment-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-operation-present :edit)
                                        (ltu/is-operation-present :delete)
                                        (ltu/is-operation-present :start)
                                        (ltu/is-key-value :state "CREATED"))

                start-url           (ltu/get-op-url deployment-response "start")]

            ;; start the deployment
            (-> session-user
                (request start-url
                         :request-method :post)
                (ltu/body->edn)
                (ltu/is-status 202)
                (ltu/location-url))

            ;; verify that the state has changed
            (-> session-user
                (request deployment-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-present :edit)
                (ltu/is-operation-present :stop)
                (ltu/is-operation-absent :delete)
                (ltu/is-operation-absent :start)
                (ltu/is-key-value :state "STARTING"))

            ;; the deployment would be set to "STARTED" via the job
            ;; for the tests, set this manually to continue with the workflow
            (-> session-admin
                (request deployment-url
                         :request-method :put
                         :body (json/write-str {:state "STARTED"}))
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; create callback
            (let [callback-body       {:action          cdu/action-name
                                       :target-resource {:href deployment-id}
                                       :data            {:image  {:image-name image-name
                                                                  :tag        new-image-tag}
                                                         :commit new-commit-msg}}

                  callback-id         (-> session-admin
                                          (request callback-base-uri
                                                   :request-method :post
                                                   :body (json/write-str callback-body))
                                          (ltu/body->edn)
                                          (ltu/is-status 201)
                                          (ltu/body)
                                          :resource-id)

                  callback-url        (str p/service-context callback-id)

                  create-notification {:callback          callback-id
                                       :category          "x"
                                       :message           "x"
                                       :content-unique-id "x"}

                  notification-id     (-> session-admin
                                          (request (str p/service-context notif/resource-type)
                                                   :request-method :post
                                                   :body (json/write-str create-notification))
                                          (ltu/body->edn)
                                          (ltu/is-status 201)
                                          (ltu/body)
                                          :resource-id)

                  notification-uri    (str p/service-context notification-id)

                  callback-execute    (-> session-admin
                                          (request callback-url)
                                          (ltu/body->edn)
                                          (ltu/is-status 200)
                                          (ltu/get-op-url "execute"))]

              ;; notification exist
              (-> session-admin
                  (request notification-uri)
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; execute callback and check created job
              (let [job-url (-> session-admin
                                (request callback-execute)
                                (ltu/body->edn)
                                (ltu/is-status 202)
                                (ltu/location-url))]
                (-> session-admin
                    (request job-url)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :state "QUEUED")
                    (ltu/is-key-value :action "update_deployment")
                    (ltu/is-key-value :target-resource {:href deployment-id})))

              ;; notification has been deleted after execution of the callback
              (-> session-admin
                  (request notification-uri)
                  (ltu/body->edn)
                  (ltu/is-status 404))

              (let [new-content (-> session-admin
                                    (request deployment-url)
                                    (ltu/body->edn)
                                    (ltu/is-key-value :state "UPDATING")
                                    :response
                                    :body
                                    :module
                                    :content)]
                (is (= (-> new-content :commit) new-commit-msg))
                (is (= (-> new-content :image :image-name) image-name))
                (is (= (-> new-content :image :tag) new-image-tag)))

              ;; delete callback
              (-> session-admin
                  (request callback-url
                           :request-method :delete)
                  (ltu/is-status 200))))

          ;; on success, the deployment update job would set the deployment state to "STARTED"
          ;; for the tests, set this manually to continue with the workflow
          (-> session-admin
              (request deployment-url
                       :request-method :put
                       :body (json/write-str {:state "STARTED"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; stop deployment so that we can delete it
          (-> session-admin
              (request deployment-url
                       :request-method :put
                       :body (json/write-str {:state "STOPPED"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; delete deployment
          (-> session-admin
              (request deployment-url
                       :request-method :delete)
              (ltu/is-status 200)))

        ;; delete module
        (-> session-user
            (request (str p/service-context module-id)
                     :request-method :delete)
            (ltu/is-status 200))))))

