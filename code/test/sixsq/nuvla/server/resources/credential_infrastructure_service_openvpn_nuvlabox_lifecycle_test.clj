(ns sixsq.nuvla.server.resources.credential-infrastructure-service-openvpn-nuvlabox-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-nuvlabox :as ctison]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ctison/resource-url)))


(deftest lifecycle
  (let [session                   (-> (ltu/ring-app)
                                      session
                                      (content-type "application/json"))
        session-admin             (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user              (header session authn-info-header "user/jane group/nuvla-user  group/nuvla-anon")
        session-nuvlabox          (header session authn-info-header "group/nuvla-nuvlabox group/nuvla-user group/nuvla-anon")
        session-anon              (header session authn-info-header "user/unknown group/nuvla-anon")

        name-attr                 "name"
        description-attr          "description"
        tags-attr                 ["one", "two"]

        openvpn-common-name-value "common-name-example"
        openvpn-certificate-value "my-public-certificate"

        parent-value              "infrastructure-service/alpha"

        href                      (str ct/resource-type "/" ctison/method)
        template-url              (str p/service-context ct/resource-type "/" ctison/method)

        template                  (-> session-nuvlabox
                                      (request template-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/body))

        create-import-no-href     {:template (ltu/strip-unwanted-attrs template)}

        create-import-href        {:name        name-attr
                                   :description description-attr
                                   :tags        tags-attr
                                   :template    {:href                href
                                                 :parent              parent-value
                                                 :openvpn-common-name openvpn-common-name-value
                                                 :openvpn-certificate openvpn-certificate-value}}]

    ;; nuvla user is not able to see the template
    (-> session-user
        (request template-url)
        (ltu/body->edn)
        (ltu/is-status 403)
        (ltu/body))

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-nuvlabox]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit)))

    ;; anonymous credential collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; creating a new credential without reference will fail for all types of users
    (doseq [session [session-admin session-nuvlabox session-anon]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-import-no-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-import-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; create a credential as a normal user
    (let [resp    (-> session-nuvlabox
                      (request base-uri
                               :request-method :post
                               :body (json/write-str create-import-href))
                      (ltu/body->edn)
                      (ltu/is-status 201))
          id      (ltu/body-resource-id resp)
          uri     (-> resp
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; admin should be able to see and delete credential
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-present :edit))

      ;; admin should be able to see and delete credential
      (-> session-nuvlabox
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-absent :edit))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags
                    openvpn-common-name openvpn-certificate
                    parent]} (-> session-nuvlabox
                                 (request abs-uri)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))]

        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is (= openvpn-common-name openvpn-common-name-value))
        (is (= openvpn-certificate openvpn-certificate-value))
        (is (= parent parent-value)))

      ;; delete the credential
      (-> session-nuvlabox
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
