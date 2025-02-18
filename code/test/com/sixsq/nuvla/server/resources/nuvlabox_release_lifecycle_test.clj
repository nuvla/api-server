(ns com.sixsq.nuvla.server.resources.nuvlabox-release-lifecycle-test
  (:require
    [clojure.test :refer [deftest testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.nuvlabox-release :as t]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


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
        session-admin          (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012347 user/abcdef01-abcd-abcd-abcd-abcdef012347 group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user           (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012346 user/abcdef01-abcd-abcd-abcd-abcdef012346 group/nuvla-user group/nuvla-anon")

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
                                                 :scope "core"}]}]

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (j/write-value-as-string valid-nuvlabox-release))
        (ltu/body->edn)
        (ltu/is-status 403))

    (let [abs-url     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string valid-nuvlabox-release))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location-url))]

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string valid-nuvlabox-release))
          (ltu/body->edn)
          (ltu/is-status 403))

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

      (-> session-admin
          (request abs-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :published false)
          (ltu/is-key-value :name "my-nuvlabox-release")
          (ltu/is-operation-present :edit)
          (ltu/is-operation-present :delete))

      (-> session-admin
          (request abs-url
                   :request-method :put
                   :body (j/write-value-as-string {:name      "edit name"
                                          :published true}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :name "edit name")
          (ltu/is-key-value :published true))

      (-> session-admin
          (request abs-url :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))
    (testing "when published flag is set on creation, it's taken into account"
      (doseq [published [true false]]
        (let [abs-url (-> session-admin
                          (request base-uri
                                   :request-method :post
                                   :body (-> valid-nuvlabox-release
                                             (assoc :published published)
                                             j/write-value-as-string))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location-url))]
          (-> session-admin
              (request abs-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :published published)))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[resource-uri :post]])))
