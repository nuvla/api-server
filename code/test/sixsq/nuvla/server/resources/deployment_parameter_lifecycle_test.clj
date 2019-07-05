(ns sixsq.nuvla.server.resources.deployment-parameter-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-parameter :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def parent-id "deployment/324c6138-aaaa-bbbb-cccc-af3ad15815db")


(def node-id "machine")


(def parameter-name "param1")


(def valid-entry
  {:name    parameter-name
   :parent  parent-id
   :node-id node-id
   :acl     {:owners   ["group/nuvla-admin"]
             :edit-acl ["user/jane"]}})


(def expected-dp-id (str "deployment-parameter/" (t/parameter->uuid parent-id node-id parameter-name)))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-jane  (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown group/nuvla-anon")]

    ;; admin user collection query should succeed but be empty (no records created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; normal user collection query should succeed but be empty (no records created yet)
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-absent :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))


    ;; anonymous credential collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))


    ;; create a deployment parameter as a admin user
    (let [resp-test     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-entry))
                            (ltu/body->edn)
                            (ltu/is-status 201))

          id-test       (get-in resp-test [:response :body :resource-id])

          location-test (str p/service-context (-> resp-test ltu/location))

          test-uri      (str p/service-context id-test)]

      (-> session-jane
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      (is (= location-test test-uri))

      ;; admin should be able to see everyone's records. Deployment parameter id is predictable
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-id expected-dp-id)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-present :edit))

      ;; user can edit, but parent, name, and node-id cannot be changed
      (let [bad-id   "deployment/324c6138-0484-34b5-bf35-af3ad15815db"
            original (-> session-jane
                         (request test-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/body))]

        (-> session-jane
            (request test-uri
                     :request-method :put
                     :body (json/write-str {:parent  bad-id
                                            :name    "bad-name"
                                            :node-id "bad-node"
                                            :value   "OK!"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [{:keys [id name node-id value]} (-> session-jane
                                                  (request test-uri)
                                                  (ltu/body->edn)
                                                  (ltu/is-status 200)
                                                  (ltu/body))]

          (is (= id (:id original)))
          (is (not= bad-id id))

          (is (= name (:name original)))
          (is (not= name "bad-name"))

          (is (= node-id (:node-id original)))
          (is (not= node-id "bad-node"))

          (is (= value "OK!"))))

      (-> session-anon
          (request test-uri
                   :request-method :put
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; search
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; delete
      (-> session-jane
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; resource should be deleted
      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
