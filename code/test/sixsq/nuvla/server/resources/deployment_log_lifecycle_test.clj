(ns sixsq.nuvla.server.resources.deployment-log-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-log :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def parent-id "deployment/324c6138-aaaa-bbbb-cccc-af3ad15815db")


(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")


(def service "my-service-name")


(def parameter-name "param1")


(def valid-entry
  {:name    parameter-name
   :parent  parent-id
   :service "my-service"
   :log     ["my-log-information"]
   :acl     {:owners   ["group/nuvla-admin"]
             :edit-acl ["user/jane"]}})

(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-jane  (header session authn-info-header (str "user/jane group/nuvla-user group/nuvla-anon" " " session-id))
        session-other (header session authn-info-header "user/other group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown group/nuvla-anon")]

    ;; admin deployment-log collection query should succeed but be empty (no logs created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; normal deployment-log collection query should succeed but be empty (no logs created yet)
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-absent :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; anonymous deployment-log collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; creation should only be allowed by admin; others get 403
    (doseq [session [session-anon session-jane session-other]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; create a deployment log as an admin user using internal utility function
    (let [resp-test     {:response (t/create-log parent-id session-id service)}

          id-test       (get-in resp-test [:response :body :resource-id])

          location-test (str p/service-context (-> resp-test ltu/location))

          test-uri      (str p/service-context id-test)]

      (is (= location-test test-uri))

      ;; admin should be able to see everyone's logs.
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-present :edit))

      ;; other users should not see the logs
      (-> session-other
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user can edit, but parent and service cannot be changed
      (let [bad-id    "deployment/324c6138-0484-34b5-bf35-af3ad15815db"
            resp      (-> session-jane
                          (request test-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present :fetch)
                          (ltu/is-operation-present :edit)
                          (ltu/is-operation-absent :delete))

            fetch-url (ltu/get-op-url resp "fetch")

            original  (ltu/body resp)]

        (-> session-jane
            (request test-uri
                     :request-method :put
                     :body (json/write-str {:parent         bad-id
                                            :name           "updated-name"
                                            :service        "bad-service"
                                            :last-timestamp "1964-08-25T10:00:00.00Z"
                                            :log            ["OK!"]}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [{:keys [id name service log last-timestamp]} (-> session-jane
                                                               (request test-uri)
                                                               (ltu/body->edn)
                                                               (ltu/is-status 200)
                                                               (ltu/body))]

          (is (= id (:id original)))
          (is (not= bad-id id))

          (is (not= name (:name original)))
          (is (= name "updated-name"))

          (is (= service (:service original)))
          (is (not= service "bad-service"))

          (is (= last-timestamp "1964-08-25T10:00:00.00Z"))

          (is (= log ["OK!"])))

        ;; check the actions
        (-> session-jane
            (request fetch-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 202)
            (ltu/is-key-value #(-> % (str/split #" ") first) :message "starting"))

        (-> session-other
            (request fetch-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; check the fetch action doesn't create additional job if another one is pending
        (-> session-jane
            (request fetch-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 202)
            (ltu/is-key-value #(-> % (str/split #" ") first) :message "existing")))

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

      ;; logs cannot be deleted by user
      (-> session-jane
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; logs can be deleted by user
      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; resource should be deleted
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
