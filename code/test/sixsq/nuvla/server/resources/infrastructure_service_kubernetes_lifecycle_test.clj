(ns sixsq.nuvla.server.resources.infrastructure-service-kubernetes-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [sixsq.nuvla.server.resources.infrastructure-service :as t]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as service-group]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [sixsq.nuvla.server.resources.infrastructure-service-template-kubernetes :as infra-service-tpl-kubernetes]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def service-group-base-uri (str p/service-context service-group/resource-type))


(def credential-base-uri (str p/service-context credential/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        ;; setup a service-group to act as parent for service
        valid-service-group {:name          "my-service-group"
                             :description   "my-description"
                             :documentation "http://my-documentation.org"}

        service-group-id (-> session-user
                             (request service-group-base-uri
                                      :request-method :post
                                      :body (json/write-str valid-service-group))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

        ;; setup a generic service to act as the 'cloud'
        valid-create {:name        "my-cloud-service"
                      :description "my-cloud-description"
                      :tags        ["alpha"]
                      :template    {:href     (str infra-service-tpl/resource-type "/"
                                                   infra-service-tpl-generic/method)
                                    :acl      valid-acl
                                    :parent   service-group-id
                                    :type     "cloud"
                                    :endpoint "https://cloud.example.org/api"
                                    :nodes    []
                                    :state    "STARTED"}}


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
                      :template    {:href               (str infra-service-tpl/resource-type "/"
                                                             infra-service-tpl-kubernetes/method)
                                    :parent             service-group-id
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
          (is (= "STARTING" (:state service)))
          (is (= credential-id (:management-credential-id service))))

        ;; can delete resource
        (-> session
            (request abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))))
