(ns sixsq.nuvla.server.resources.nuvlabox-release-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-release :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-user"]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon           (-> (ltu/ring-app)
                                   session
                                   (content-type "application/json"))
        session-admin          (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012347 group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user           (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012346 group/nuvla-user group/nuvla-anon")

        valid-nuvlabox-release {:name          "my-nuvlabox-release"
                                :description   "my-nuvlabox-release description"

                                :acl           valid-acl
                                :release       "1.0.0"
                                :release-date  "2020-01-28T13:48:03Z"
                                :release-notes "added this \n changed that \r\n"
                                :url           "https://github.com/nuvlabox/deployment/releases/tag/1.0.0"
                                :pre-release   false
                                :compose-files [{:file  "version: '3.7'\n\nservices:"
                                                 :name  "docker-compose.yml"
                                                 :scope "core"}]
                                }]

    ;; admin query succeeds but is empty
    (-> session-admin
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
                 :body (json/write-str valid-nuvlabox-release))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check nuvlabox-release creation
    (let [admin-uri     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-nuvlabox-release))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          admin-abs-uri (str p/service-context admin-uri)

          user-uri      (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-nuvlabox-release))
                            (ltu/body->edn)
                            (ltu/is-status 403))

          user-abs-uri  (str p/service-context user-uri)]

      ;; admin should see 1 nuvlabox-release resources
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      ;; ocre user only sees 1 cause its POST failed, but has view rights
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      ;; verify contents of admin nuvlabox-release
      (let [nuvlabox-release-full (-> session-admin
                                      (request admin-abs-uri)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :edit)
                                      (ltu/is-operation-present :delete))
            nuvlabox-release      (:body (:response nuvlabox-release-full))]

        (is (= "my-nuvlabox-release" (:name nuvlabox-release)))

        ;; verify that an edit works
        (let [updated (assoc nuvlabox-release :name "edit name")]

          (-> session-admin
              (request admin-abs-uri
                       :request-method :put
                       :body (json/write-str updated))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/body))

          (let [updated-body (-> session-admin
                                 (request admin-abs-uri)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))]

            (is (= "edit name" (:name updated-body))))))

      ;; admin can delete the nuvlabox-release
      (-> session-admin
          (request admin-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]])))
