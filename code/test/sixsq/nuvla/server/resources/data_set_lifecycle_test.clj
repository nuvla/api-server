(ns sixsq.nuvla.server.resources.data-set-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-set :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon   (-> (ltu/ring-app)
                           session
                           (content-type "application/json"))
        session-admin  (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user   (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        valid-data-set {:name               "my-data-set"
                        :description        "my-data-set description"

                        :module-filter      "(filter='module')"
                        :data-object-filter "(filter='object')"
                        :data-record-filter "(filter='record')"

                        ;:acl                valid-acl
                        }]

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
                 :body (json/write-str valid-data-set))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check data-set creation
    (let [admin-uri     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-data-set))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          admin-abs-uri (str p/service-context admin-uri)

          user-uri      (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-data-set))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          user-abs-uri  (str p/service-context user-uri)]

      ;; admin should see 2 data-set resources
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 2))

      ;; user should see only 1
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      ;; verify contents of admin data-set
      (let [data-set (-> session-admin
                         (request admin-abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/is-operation-present :edit)
                         (ltu/is-operation-present :delete)
                         (ltu/body))]

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
              (ltu/body))

          (let [updated-body (-> session-admin
                                 (request admin-abs-uri)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))]

            (is (= "(filter='updated')" (:module-filter updated-body))))))

      ;; admin can delete the data-set
      (-> session-admin
          (request admin-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user can delete the data-set
      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
