(ns sixsq.nuvla.server.resources.credential.vpn-utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.configuration-template :as configuration-tpl]
    [sixsq.nuvla.server.resources.configuration-template-vpn-api :as configuration-tpl-vpn]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-vpn
     :as infra-srvc-tpl-vpn]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(def base-uri (str p/service-context credential/resource-type))

(defn credential-vpn-lifecycle-test
  [method vpn-scope user-id claims method-not-corresponding-to-scope]
  (let [session                  (-> (ltu/ring-app)
                                     session
                                     (content-type "application/json"))
        session-admin            (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user-or-nuvlabox (header session authn-info-header claims)
        session-anon             (header session authn-info-header "user/unknown group/nuvla-anon")

        name-attr                "name"
        description-attr         "description"
        tags-attr                ["one", "two"]

        common-name-value        user-id
        certificate-value        "my-public-certificate"
        inter-ca-values          ["certif-1"]
        private-key-value        "private key visible only once at creation time"

        infra-service-create     {:template {:href          (str infra-service-tpl/resource-type "/"
                                                                 infra-srvc-tpl-vpn/method)
                                             :vpn-scope vpn-scope
                                             :acl           {:owners   ["nuvla/admin"]
                                                             :view-acl ["nuvla/user"
                                                                        "nuvla/nuvlabox"]}}}
        infra-service-id         (-> session-admin
                                     (request (str p/service-context infra-service/resource-type)
                                              :request-method :post
                                              :body (json/write-str infra-service-create))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/location))

        configuration-create     {:template
                                  {:href                    (str configuration-tpl/resource-type "/"
                                                                 configuration-tpl-vpn/service)
                                   :instance                "vpn"
                                   :endpoint                "http://vpn.test"
                                   :infrastructure-services [infra-service-id]}}

        href                     (str ct/resource-type "/" method)
        bad-href                 (str ct/resource-type "/" method-not-corresponding-to-scope)
        template-url             (str p/service-context ct/resource-type "/" method)

        template                 (-> session-user-or-nuvlabox
                                     (request template-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/body))

        create-import-no-href    {:template (ltu/strip-unwanted-attrs template)}

        create-import-href       {:name        name-attr
                                  :description description-attr
                                  :tags        tags-attr
                                  :template    {:href   href
                                                :parent infra-service-id}}]

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-user-or-nuvlabox]]
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
    (doseq [session [session-admin session-user-or-nuvlabox session-anon]]
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

    (with-redefs [vpn-utils/generate-credential (fn [_ _ _ _]
                                                      {:certificate     certificate-value
                                                       :common-name     common-name-value
                                                       :intermediate-ca inter-ca-values
                                                       :private-key     private-key-value})]
      (-> session-user-or-nuvlabox
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-import-href))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/is-key-value
            #(str/starts-with? % "No vpn api endpoint found for ") :message true))

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
            :body (json/write-str (assoc-in create-import-href [:template :href] bad-href)))
          (ltu/body->edn)
          (ltu/is-key-value :message
                            "Bad infrastructure service scope for selected credential template!")
          (ltu/is-status 400))

      ;; create a credential as a normal user
      (let [resp    (-> session-user-or-nuvlabox
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-import-href))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/is-key-value :private-key private-key-value)
                        (ltu/is-key-value :common-name common-name-value)
                        (ltu/is-key-value :intermediate-ca inter-ca-values))
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
        (-> session-user-or-nuvlabox
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-absent :edit))

        ;; ensure credential contains correct information
        (let [{:keys [name description tags
                      vpn-common-name vpn-certificate
                      vpn-intermediate-ca parent]} (-> session-user-or-nuvlabox
                                                           (request abs-uri)
                                                           (ltu/body->edn)
                                                           (ltu/is-status 200)
                                                           (ltu/body))]

          (is (= name name-attr))
          (is (= description description-attr))
          (is (= tags tags-attr))
          (is (= vpn-common-name common-name-value))
          (is (= vpn-certificate certificate-value))
          (is (= parent infra-service-id))
          (is (= vpn-intermediate-ca inter-ca-values)))

        (-> session-user-or-nuvlabox
            (request base-uri
                     :request-method :post
                     :body (json/write-str create-import-href))
            (ltu/body->edn)
            (ltu/is-key-value :message (str "Credential VPN already exist for your account on "
                                            "selected VPN infrastructure service!"))
            (ltu/is-status 400))

        ;; credential should not be deleted if vpn api respond with error
        (with-redefs [vpn-utils/delete-credential
                      (fn [_ _]
                        (throw (ex-info "test " {})))]
          (-> session-user-or-nuvlabox
              (request abs-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 500)))

        ;; credential wasn't deleted
        (-> session-user-or-nuvlabox
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; delete credential should succeed
        (with-redefs [vpn-utils/delete-credential (fn [_ _])]
          (-> session-user-or-nuvlabox
              (request abs-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200)))

        ))
    ))

