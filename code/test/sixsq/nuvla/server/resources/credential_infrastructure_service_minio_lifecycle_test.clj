(ns sixsq.nuvla.server.resources.credential-infrastructure-service-minio-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as cred-tpl]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-minio :as cred-tpl-minio]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest lifecycle
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")

        name-attr "name"
        description-attr "description"
        tags-attr ["one", "two"]

        username-value "my-username"
        password-value "my-password"

        infrastructure-services-value ["infrastructure-service/alpha"
                                       "infrastructure-service/beta"]

        href (str cred-tpl/resource-type "/" cred-tpl-minio/method)
        template-url (str p/service-context cred-tpl/resource-type "/" cred-tpl-minio/method)

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     :response
                     :body)

        create-import-no-href {:template (ltu/strip-unwanted-attrs template)}

        create-import-href {:name        name-attr
                            :description description-attr
                            :tags        tags-attr
                            :template    {:href                    href
                                          :infrastructure-services infrastructure-services-value
                                          :username                username-value
                                          :password                password-value}}]

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit")))

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
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-import-href))
                   (ltu/body->edn)
                   (ltu/is-status 201))
          id (get-in resp [:response :body :resource-id])
          uri (-> resp
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
            (ltu/is-operation-present "delete")
            (ltu/is-operation-present "edit")))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags
                    username password
                    infrastructure-services]} (-> session-user
                                                  (request abs-uri)
                                                  (ltu/body->edn)
                                                  (ltu/is-status 200)
                                                  :response
                                                  :body)]

        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is (= username username-value))
        (is (= password password-value))
        (is (= infrastructure-services infrastructure-services-value)))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))












