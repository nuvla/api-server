(ns sixsq.nuvla.server.resources.deployment-set-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment :as deployment]
    [sixsq.nuvla.server.resources.deployment-set :as t]
    [sixsq.nuvla.server.resources.deployment.utils :as deployment-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))
(def deployment-base-uri (str p/service-context deployment/resource-type))


(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")

(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))

(def dep-set-name "deployment set for testing")
(def valid-deployment-set {:name dep-set-name
                           :spec {:targets      ["credential/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]
                                  :applications ["module/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]}})

(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
        session-user  (header session-anon authn-info-header
                              (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))]

    (testing "admin/user query succeeds but is empty"
      (doseq [session [session-admin session-user]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)
            (ltu/is-operation-present :add)
            (ltu/is-operation-absent :delete)
            (ltu/is-operation-absent :edit))))

    (testing "anon query fails"
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "anon create must fail"
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "create must be possible for user"
      (let [{{{:keys [resource-id
                      location]} :body}
             :response} (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-deployment-set))
                            (ltu/body->edn)
                            (ltu/is-status 202))

            dep-set-url       (str p/service-context resource-id)
            authn-payload     {"authn-info" {"active-claim" "user/jane"
                                             "claims"       ["group/nuvla-anon"
                                                             "user/jane"
                                                             "group/nuvla-user"
                                                             session-id]
                                             "user-id"      "user/jane"}}
            job-payload-start (merge authn-payload
                                     {"filter" (str "deployment-set='" resource-id "' "
                                                    "and (state='CREATED' or state='STOPPED')"
                                                    )})
            job-payload-stop  (merge authn-payload
                                     {"filter" (str "deployment-set='" resource-id "'")})]

        (testing "user query should see one document"
          (-> session-user
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-resource-uri t/collection-type)
              (ltu/is-count 1)))

        (testing "user retrieve should work and contain job"
          (-> session-user
              (request dep-set-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "CREATING")
              (ltu/is-key-value map? :spec true)))

        (testing "job is created"
          (-> session-user
              (request (str p/service-context location))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :action "create_deployment_set")
              (ltu/is-key-value :href :target-resource resource-id)))

        (testing "start action will create a start_deployment_set job"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (-> session-user
                                 (request dep-set-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body)
                                 (assoc :state "CREATED")
                                 (json/write-str)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "CREATED"))
          (let [job-url (-> session-user
                            (request (str dep-set-url "/start"))
                            (ltu/body->edn)
                            (ltu/is-status 202)
                            (ltu/location-url))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "start_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload-start))))

        (testing "stop will not be possible in state CREATED"
          (-> session-user
              (request (str dep-set-url "/stop"))
              (ltu/body->edn)
              (ltu/is-status 409)))

        (testing "stop action will create a stop_deployment_set job"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (-> session-user
                                 (request dep-set-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body)
                                 (assoc :state "STARTED")
                                 (json/write-str)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "STARTED"))
          (let [job-url (-> session-user
                            (request (str dep-set-url "/stop"))
                            (ltu/body->edn)
                            (ltu/is-status 202)
                            (ltu/location-url))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "stop_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload-stop))))
        ))
    ))

(deftest lifecycle-deployment-detach
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
          {{{:keys [resource-id]} :body}
           :response} (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str valid-deployment-set))
                          (ltu/body->edn)
                          (ltu/is-status 202))

          dep-set-url      (str p/service-context resource-id)

          valid-deployment {:module         {:href "module/x"}
                            :deployment-set resource-id}
          dep-url          (with-redefs [deployment-utils/resolve-from-module identity]
                             (-> session-user
                                 (request deployment-base-uri
                                          :request-method :post
                                          :body (json/write-str valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location-url)))
          detach-url       (-> session-user
                               (request dep-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-key-value :deployment-set resource-id)
                               (ltu/is-key-value :deployment-set-name dep-set-name)
                               (ltu/is-operation-present :detach)
                               (ltu/get-op-url :detach))
          new-dep-set-name "dep set name changed"]

      (testing "deployment set name is refreshed on edit of deployment"
        (-> session-user
            (request dep-set-url
                     :request-method :put
                     :body (json/write-str {:name new-dep-set-name}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request dep-url
                     :request-method :put
                     :body (json/write-str {}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :deployment-set resource-id)
            (ltu/is-key-value :deployment-set-name new-dep-set-name)))

      (testing "user is able to detach deployment set"
        (-> session-user
           (request detach-url)
           (ltu/body->edn)
           (ltu/is-status 200)
           (ltu/is-key-value :deployment-set nil)
           (ltu/is-key-value :deployment-set-name nil)
           (ltu/is-operation-absent :detach))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
