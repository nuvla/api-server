(ns sixsq.nuvla.server.resources.credential-infrastructure-service-openvpn-customer-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-customer
     :as ctisoc]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-nuvlabox
     :as ctison]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.configuration-template-openvpn-api :as configuration-tpl-openvpn]
    [sixsq.nuvla.server.resources.configuration-template :as configuration-tpl]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.credential.openvpn-utils :as openvpn-utils]
    [sixsq.nuvla.server.resources.infrastructure-service-template-openvpn
     :as infra-srvc-tpl-openvpn]
    [clojure.tools.logging :as log]
    [clojure.string :as str]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ctisoc/resource-url)))


(deftest lifecycle
  (let [session               (-> (ltu/ring-app)
                                  session
                                  (content-type "application/json"))
        session-admin         (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user          (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon          (header session authn-info-header "user/unknown group/nuvla-anon")

        name-attr             "name"
        description-attr      "description"
        tags-attr             ["one", "two"]

        common-name-value     "user/jane"
        certificate-value     "my-public-certificate"
        inter-ca-values       ["certif-1"]

        infra-service-create  {:template {:href          (str infra-service-tpl/resource-type "/"
                                                              infra-srvc-tpl-openvpn/method)
                                          :openvpn-scope "customer"}}
        infra-service-id      (-> session-user
                                  (request (str p/service-context infra-service/resource-type)
                                           :request-method :post
                                           :body (json/write-str infra-service-create))
                                  (ltu/body->edn)
                                  (ltu/is-status 201)
                                  (ltu/location))

        configuration-create  {:template
                               {:href                    (str configuration-tpl/resource-type "/"
                                                              configuration-tpl-openvpn/service)
                                :instance                "openvpn"
                                :endpoint                "http://openvpn.test"
                                :infrastructure-services [infra-service-id]}}

        href                  (str ct/resource-type "/" ctisoc/method)
        href-nuvlabox         (str ct/resource-type "/" ctison/method)
        template-url          (str p/service-context ct/resource-type "/" ctisoc/method)

        template              (-> session-user
                                  (request template-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/body))

        create-import-no-href {:template (ltu/strip-unwanted-attrs template)}

        create-import-href    {:name        name-attr
                               :description description-attr
                               :tags        tags-attr
                               :template    {:href   href
                                             :parent infra-service-id}}]

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-user]]
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
    (doseq [session [session-admin session-user session-anon]]
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

    (with-redefs [openvpn-utils/generate-credential (fn [_ _ _]
                                                      {:certificate     certificate-value
                                                       :common-name     common-name-value
                                                       :intermediate-ca inter-ca-values})]
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-import-href))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/is-key-value
            #(str/starts-with? % "No openvpn api endpoint found for ") :message true))

      (-> session-admin
          (request (str p/service-context configuration/resource-type)
                   :request-method :post
                   :body (json/write-str configuration-create))
          (ltu/body->edn)
          (ltu/is-status 201))

      ;; even with admin the create will fail when using bad template and scope
      (-> session-admin
          (request
            base-uri
            :request-method :post
            :body (json/write-str (assoc-in create-import-href [:template :href] href-nuvlabox)))
          (ltu/body->edn)
          (ltu/is-key-value :message
                            "Bad infrastructure service scope for selected credential template!")
          (ltu/is-status 400))

      ;; create a credential as a normal user
      (let [resp    (-> session-user
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


        ;; user should be able to see and delete credential
        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-absent :edit))

        ;; ensure credential contains correct information
        (let [{:keys [name description tags
                      openvpn-common-name openvpn-certificate
                      openvpn-intermediate-ca parent]} (-> session-user
                                                           (request abs-uri)
                                                           (ltu/body->edn)
                                                           (ltu/is-status 200)
                                                           (ltu/body))]

          (is (= name name-attr))
          (is (= description description-attr))
          (is (= tags tags-attr))
          (is (= openvpn-common-name common-name-value))
          (is (= openvpn-certificate certificate-value))
          (is (= parent infra-service-id))
          (is (= openvpn-intermediate-ca inter-ca-values)))

        (-> session-user
            (request base-uri
                     :request-method :post
                     :body (json/write-str create-import-href))
            (ltu/body->edn)
            (ltu/is-key-value :message "Credential with following common-name already exist!")
            (ltu/is-status 400))

        ;; delete the credential
        (-> session-user
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))
        ))))
