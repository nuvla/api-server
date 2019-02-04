(ns com.sixsq.slipstream.ssclj.resources.deployment-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.deployment :as deployment]
    [com.sixsq.slipstream.ssclj.resources.deployment-template :as deployment-template]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.module-lifecycle-test :as module-test]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context deployment/resource-url))

(def deployment-template-collection-uri (str p/service-context (u/de-camelcase deployment-template/resource-name)))

(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")
        module-uri (-> session-user
                       (request module-test/base-uri
                                :request-method :post
                                :body (json/write-str (assoc module-test/valid-entry
                                                        :content module-test/valid-image)))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))
        valid-deployment-template-create {:module {:href module-uri}}
        deployment-template-uri (-> session-user
                                    (request deployment-template-collection-uri
                                             :request-method :post
                                             :body (json/write-str valid-deployment-template-create))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/location))
        valid-deployment {:deploymentTemplate {:href deployment-template-uri} :name "dep1" :description "dep1 desc"}
        valid-deployment-from-module {:deploymentTemplate {:module {:href module-uri}}}]

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-deployment))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anonymous query should also fail
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ; adding the deployment and reading it as user should succeed
    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-deployment))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          uri-deployment-fron-module (-> session-user
                                         (request base-uri
                                                  :request-method :post
                                                  :body (json/write-str valid-deployment-from-module))
                                         (ltu/body->edn)
                                         (ltu/is-status 201)
                                         (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))
          deployment (-> session-user
                         (request (str abs-uri "?$select=description")
                                  :request-method :put
                                  :body (json/write-str {:name         "dep 1 new name"
                                                         :clientAPIKey "this field should be ignored, not editable"}))
                         (ltu/body->edn)
                         (ltu/is-status 200))
          cred-uri (str p/service-context
                        (u/de-camelcase
                          (get-in deployment [:response :body :clientAPIKey :href])))]

      ;; user query: ok
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri deployment/collection-uri)
          (ltu/is-count #(= 2 %))
          (ltu/entries deployment/resource-tag))

      ;; user is able to change name and remove existing description attribute
      ;; but should not able to edit clientAPIKey
      (-> deployment
          (ltu/is-key-value :name "dep 1 new name")
          (#(is (not (contains? % :description)))))

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :name "dep 1 new name")
          (ltu/is-key-value map? :clientAPIKey true)
          (#(is (not (contains? (-> % :response :body) :description)))))

      ;; generated api key secret are with user identity claims
      (-> session-user
          (request cred-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :claims {:identity "jane", :roles ["USER" "ANON"]}))

      ;; admin query: ok
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri deployment/collection-uri)
          (ltu/is-count #(= 2 %))
          (ltu/entries deployment/resource-tag))

      ;; user view: OK
      (let [
            ;; CREATED state on creation
            start-op (-> session-user
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/is-operation-present "edit")
                         (ltu/is-operation-present "delete")
                         (ltu/is-operation-present (:start c/action-uri))
                         (ltu/is-operation-absent (:stop c/action-uri))
                         (ltu/get-op "start"))
            abs-start-uri (str p/service-context (u/de-camelcase start-op))

            start-job-uri (-> session-user
                              (request abs-start-uri
                                       :request-method :post)
                              (ltu/body->edn)
                              (ltu/is-status 202)
                              (ltu/location))

            abs-start-job-uri (str p/service-context start-job-uri)

            ;; STARTING state after start action
            stop-op (-> session-user
                        (request abs-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-operation-present "edit")
                        (ltu/is-operation-absent "delete")
                        (ltu/is-operation-absent (:start c/action-uri))
                        (ltu/is-operation-present (:stop c/action-uri))
                        (ltu/get-op "stop"))
            abs-stop-uri (str p/service-context (u/de-camelcase stop-op))

            stop-job-uri (-> session-user
                             (request abs-stop-uri
                                      :request-method :post)
                             (ltu/body->edn)
                             (ltu/is-status 202)
                             (ltu/location))
            abs-stop-job-uri (str p/service-context stop-job-uri)]

        (-> session-user
            (request abs-start-job-uri)
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request abs-stop-job-uri)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; STOPPING state after stop action
        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "edit")
            (ltu/is-operation-absent "delete")
            (ltu/is-operation-absent (:start c/action-uri))
            (ltu/is-operation-absent (:stop c/action-uri))
            (ltu/is-key-value :state "STOPPING"))

        ;; user delete: FAIL
        (-> session-user
            (request abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 409))

        ;; user should see events created
        (-> session-user
            (request (str p/service-context "event"))
            (ltu/body->edn)
            (ltu/is-count 3))

        ))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id deployment/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
