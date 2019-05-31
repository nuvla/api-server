(ns sixsq.nuvla.server.resources.credential-infrastructure-service-google-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-google :as service-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context credential/resource-type))

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

        href                  (str ct/resource-type "/" service-tpl/method)
        template-url          (str p/service-context ct/resource-type "/" service-tpl/method)

        template              (-> session-admin
                                  (request template-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (get-in [:response :body]))

        create-import-no-href {:template (ltu/strip-unwanted-attrs template)}

        create-import-href
                              {:name        name-attr
                               :description description-attr
                               :tags        tags-attr
                               :template    {:href           href
                                             :project-id     "my-project-id"
                                             :private-key-id "abcde1234"
                                             :private-key    "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
                                             :client-email   "1234-compute@developer.gserviceaccount.com"
                                             :client-id      "98765"
                                             :parent         "infrastructure-service/service-1"}}]

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
          id      (get-in resp [:response :body :resource-id])
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
            (ltu/is-operation-present :edit)))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags
                    project-id private-key-id private-key client-email client-id]} (-> session-user
                                                                                       (request abs-uri)
                                                                                       (ltu/body->edn)
                                                                                       (ltu/is-status 200)
                                                                                       (ltu/body))]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is project-id)
        (is private-key-id)
        (is private-key)
        (is client-email)
        (is client-id))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
