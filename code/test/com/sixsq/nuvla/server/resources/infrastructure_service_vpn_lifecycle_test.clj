(ns com.sixsq.nuvla.server.resources.infrastructure-service-vpn-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.infrastructure-service :as t]
    [com.sixsq.nuvla.server.resources.infrastructure-service-group :as service-group]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template-vpn :as infra-service-tpl-vpn]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def service-group-base-uri (str p/service-context service-group/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})


(deftest check-metadata
  (mdtu/check-metadata-exists (str infra-service-tpl/resource-type "-" infra-service-tpl-vpn/method)
                              (str infra-service-tpl/resource-type "-" infra-service-tpl-vpn/method "-create")))


(deftest lifecycle
  (let [session-anon        (-> (ltu/ring-app)
                                session
                                (content-type "application/json"))
        session-admin       (header session-anon authn-info-header
                                    "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user        (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        ;; setup a service-group to act as parent for service
        valid-service-group {:name          "my-service-group"
                             :description   "my-description"
                             :documentation "http://my-documentation.org"}

        service-group-id    (-> session-user
                                (request service-group-base-uri
                                         :request-method :post
                                         :body (j/write-value-as-string valid-service-group))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location))

        service-name        "my-service"
        service-desc        "my-description"
        service-tags        ["alpha" "beta" "gamma"]

        valid-create        {:name        service-name
                             :description service-desc
                             :tags        service-tags
                             :template    {:href   (str infra-service-tpl/resource-type "/"
                                                        infra-service-tpl-vpn/method)
                                           :parent service-group-id}}
        authn-info-admin    {:user-id      "group/nuvla-admin"
                             :active-claim "group/nuvla-admin"
                             :claims       ["group/nuvla-admin" "group/nuvla-anon" "group/nuvla-user"]}
        authn-info-jane     {:user-id      "user/jane"
                             :active-claim "user/jane"
                             :claims       ["group/nuvla-anon" "user/jane" "group/nuvla-user"]}
        admin-group-name    "Nuvla Administrator Group"]

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (j/write-value-as-string valid-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; check creation
    (doseq [[session event-owners authn-info user-name-or-id]
            [[session-admin ["group/nuvla-admin"] authn-info-admin admin-group-name]
             [session-user ["group/nuvla-admin" "user/jane"] authn-info-jane "user/jane"]]]
      (let [uri     (-> session
                        (request base-uri
                                 :request-method :post
                                 :body (j/write-value-as-string valid-create))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))
            abs-uri (str p/service-context uri)]

        ;; verify contents
        (let [service (-> session
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present :edit)
                          (ltu/is-operation-present :delete)
                          (ltu/body))]

          (is (= service-name (:name service)))
          (is (= service-desc (:description service)))
          (is (= service-tags (:tags service)))
          (is (:subtype service))
          (is (nil? (:endpoint service))))

        (ltu/is-last-event uri
                           {:name               "infrastructure-service.add"
                            :description        (str user-name-or-id " added infrastructure-service " service-name)
                            :category           "add"
                            :success            true
                            :linked-identifiers []
                            :authn-info         authn-info
                            :acl                {:owners event-owners}})

        ;; can delete resource
        (-> session
            (request abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        (ltu/is-last-event uri
                           {:name               "infrastructure-service.delete"
                            :description        (str user-name-or-id " deleted infrastructure-service " service-name)
                            :category           "delete"
                            :success            true
                            :linked-identifiers []
                            :authn-info         authn-info
                            :acl                {:owners event-owners}})))))

