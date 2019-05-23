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
    [clojure.tools.logging :as log]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        valid-deployment {:acl valid-acl}]

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
    (let [user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-deployment))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

          user-abs-uri (str p/service-context user-uri)]

      ;; admin/user should see one deployment
      (doseq [session [session-user session-admin]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-resource-uri t/collection-type)
            (ltu/is-count 1)))

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
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
