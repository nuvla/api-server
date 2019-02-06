(ns sixsq.nuvla.server.resources.deployment-parameter-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-parameter :as dp]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase dp/resource-url)))

(def valid-entry
  {:name       "param1"
   :nodeID     "machine"
   :deployment {:href "deployment/uuid"}
   :acl        {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "MODIFY"}]}})


(def valid-state-entry
  {:name       "ss:state"
   :value      "Provisioning"
   :deployment {:href "deployment/uuid"}
   :acl        {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "MODIFY"}]}})

(def valid-complete-entry
  {:name       "complete"
   :nodeID     "machine"
   :deployment {:href "deployment/uuid"}
   :acl        {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "MODIFY"}]}})


(deftest lifecycle
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-jane (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")]

    ;; admin user collection query should succeed but be empty (no records created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; normal user collection query should succeed but be empty (no records created yet)
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-absent "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))


    ;; anonymous credential collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))


    ;; create a deployment parameter as a admin user
    (let [resp-test (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str valid-entry))
                        (ltu/body->edn)
                        (ltu/is-status 201))

          id-test (get-in resp-test [:response :body :resource-id])

          location-test (str p/service-context (-> resp-test ltu/location))

          test-uri (str p/service-context id-test)]

      (-> session-jane
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      (is (= location-test test-uri))

      ;; admin should be able to see everyone's records. Deployment parameter href is predictable
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-id "deployment-parameter/324c6138-0484-34b5-bf35-af3ad15815db")
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      ;; user allowed edits
      (-> session-jane
          (request test-uri
                   :request-method :put
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 200))

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

      ;;delete
      (-> session-jane
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;;record should be deleted
      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 404))

      (let [state-uri (-> session-admin
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str valid-state-entry))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

            state-abs-uri (str p/service-context (u/de-camelcase state-uri))


            complete-uri (-> session-admin
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-complete-entry))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))
            abs-complete-uri (str p/service-context (u/de-camelcase complete-uri))]

        (-> session-jane
            (request abs-complete-uri
                     :request-method :put
                     :body (json/write-str {:value "Provisioning"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-jane
            (request state-abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :value "Executing"))

        (-> session-jane
            (request abs-complete-uri
                     :request-method :put
                     :body (json/write-str {:value "Executing"}))
            (ltu/body->edn)
            (ltu/is-status 200))


        (-> session-jane
            (request state-abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :value "SendingReports"))

        ;; complete same state is idempotent
        (-> session-jane
            (request abs-complete-uri
                     :request-method :put
                     :body (json/write-str {:value "Executing"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-jane
            (request state-abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :value "SendingReports"))

        (-> session-jane
            (request abs-complete-uri
                     :request-method :put
                     :body (json/write-str {:value "SendingReports"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-jane
            (request state-abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :value "Ready"))

        ;; user should see events created
        (-> session-jane
            (request (str p/service-context "event"))
            (ltu/body->edn)
            (ltu/is-count 5))

        ))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id dp/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
