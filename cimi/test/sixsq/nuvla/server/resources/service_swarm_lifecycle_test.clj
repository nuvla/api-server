(ns sixsq.nuvla.server.resources.service-swarm-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.service :as t]
    [sixsq.nuvla.server.resources.provider :as provider]
    [sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential :as credential]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def provider-base-uri (str p/service-context provider/resource-type))


(def credential-base-uri (str p/service-context credential/resource-type))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "USER"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")

        ;; setup a provider to act as parent for service
        valid-provider {:name          "my-provider"
                        :description   "my-description"
                        :documentation "http://my-documentation.org"}

        provider-id (-> session-user
                        (request provider-base-uri
                                 :request-method :post
                                 :body (json/write-str valid-provider))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))

        ;; setup a generic service to act as the 'cloud'
        valid-create {:name        "my-cloud-service"
                      :description "my-cloud-description"
                      :tags        ["alpha"]
                      :template    {:href     "service-template/generic"
                                    :acl      valid-acl
                                    :parent   provider-id
                                    :type     "cloud"
                                    :endpoint "https://cloud.example.org/api"
                                    :state    "STARTED"}}

        cloud-service-id (-> session-user
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-create))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

        ;; setup a credential (not the right type) to reference
        href (str ct/resource-type "/" akey/method)

        create-import-href {:name        "my-credential"
                            :description "my-credential-description"
                            :tags        ["one" "two"]
                            :template    {:href href
                                          :ttl  1000}}

        credential-id (-> session-user
                          (request credential-base-uri
                                   :request-method :post
                                   :body (json/write-str create-import-href))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

        service-name "my-service"
        service-desc "my-description"
        service-tags ["alpha" "beta" "gamma"]

        valid-create {:name        service-name
                      :description service-desc
                      :tags        service-tags
                      :template    {:href               "service-template/swarm"
                                    :parent             provider-id
                                    :cloud-service      {:href cloud-service-id}
                                    :service-credential {:href credential-id}}}]

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; check creation
    (doseq [session [session-admin session-user]]
      (let [uri (-> session
                    (request base-uri
                             :request-method :post
                             :body (json/write-str valid-create))
                    (ltu/body->edn)
                    (ltu/is-status 201)
                    (ltu/location))
            abs-uri (str p/service-context uri)]

        ;; verify contents
        (let [service (-> session
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present "edit")
                          (ltu/is-operation-present "delete")
                          :response
                          :body)]

          (is (= service-name (:name service)))
          (is (= service-desc (:description service)))
          (is (= service-tags (:tags service)))
          (is (:type service))
          (is (nil? (:endpoint service)))
          (is (= "CREATED" (:state service))))

        ;; can delete resource
        (-> session
            (request abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))))
