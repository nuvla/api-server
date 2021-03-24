(ns sixsq.nuvla.server.resources.credential-infrastructure-service-registry-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as cred-tpl]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-registry
     :as cred-tpl-registry]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" cred-tpl-registry/resource-url)))


(deftest lifecycle
  (let [session               (-> (ltu/ring-app)
                                  session
                                  (content-type "application/json"))
        session-admin         (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user          (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon          (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        name-attr             "name"
        description-attr      "description"
        tags-attr             ["one", "two"]

        username-value        "my-username"
        password-value        "my-password"

        parent-value          "infrastructure-service/alpha"

        href                  (str cred-tpl/resource-type "/" cred-tpl-registry/method)
        template-url          (str p/service-context cred-tpl/resource-type "/" cred-tpl-registry/method)

        template              (-> session-admin
                                  (request template-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/body))

        create-import-no-href {:template (ltu/strip-unwanted-attrs template)}

        create-import-href    {:name        name-attr
                               :description description-attr
                               :tags        tags-attr
                               :template    {:href     href
                                             :parent   parent-value
                                             :username username-value
                                             :password password-value}}]

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

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)
            (ltu/is-operation-present :check)))

      ;; ensure credential contains correct information
      (let [credential (-> session-user
                           (request abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200))
            {:keys [name description tags
                    username password
                    parent]} (ltu/body credential)]

        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is (= username username-value))
        (is (= password password-value))
        (is (= parent parent-value))

        ;; ensure that the check action works
        (let [op-url    (ltu/get-op credential "check")
              check-url (str p/service-context op-url)]

          (-> session-user
              (request check-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 202))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
