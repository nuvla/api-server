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
    [clojure.tools.logging :as log]
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
                   :type                      "COMPONENT"

                   :logo-url                  "https://example.org/logo"

                   :data-accept-content-types ["application/json" "application/x-something"]
                   :data-access-protocols     ["http+s3" "posix+nfs"]})


(def valid-module-component {:author       "someone"
                             :commit       "wip"

                             :architecture "x86"
                             :image        {:image-name "ubuntu"
                                            :tag        "16.04"}
                             :ports        [{:protocol       "tcp"
                                             :target-port    22
                                             :published-port 8022}]})


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        ;; setup a module that can be referenced from the deployment
        module-id (-> session-user
                      (request module-base-uri
                               :request-method :post
                               :body (json/write-str (assoc valid-module :content valid-module-component)))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))

        valid-deployment {:module {:href module-id}}
        ]

    ;; admin/user query succeeds but is empty
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit")))

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
    (let [deployment-id (-> session-user
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

      (let [deployment (-> session-user
                           (request deployment-url)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-operation-present "edit")
                           (ltu/is-operation-present "delete")
                           (ltu/is-operation-present "start")
                           :response
                           :body)]

        ;; verify that api key/secret pair was created
        (is (:api-credentials deployment))

        (when-let [credential-id (-> deployment :api-credentials :api-key)]
          (let [credential-url (str p/service-context credential-id)
                credential (-> session-user
                               (request credential-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/dump)
                               :response
                               :body)]

            (is (= deployment-id (:parent credential))))))

      ;; verify contents of admin data-set
      #_(let [data-set (-> session-admin
                           (request admin-abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-operation-present "edit")
                           (ltu/is-operation-present "delete")
                           :response
                           :body)]

          (is (= "my-data-set" (:name data-set)))
          (is (= "(filter='module')" (:module-filter data-set)))

          ;; verify that an edit works
          (let [updated (assoc data-set :module-filter "(filter='updated')")]

            (-> session-admin
                (request admin-abs-uri
                         :request-method :put
                         :body (json/write-str updated))
                (ltu/body->edn)
                (ltu/is-status 200)
                :response
                :body)

            (let [updated-body (-> session-admin
                                   (request admin-abs-uri)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   :response
                                   :body)]

              (is (= "(filter='updated')" (:module-filter updated-body))))))

      ;; admin can delete the data-set
      #_(-> session-admin
            (request admin-abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

      ;; user can delete the data-set
      #_(-> session-user
            (request deployment-url :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
