(ns sixsq.nuvla.server.resources.credential-hashed-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as ct-hashed-pwd]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest lifecycle

  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-other (header session authn-info-header "user/tarzan group/nuvla-user group/nuvla-anon")
        session-anon (header session authn-info-header "user/unknown group/nuvla-anon")

        name-attr "name"
        description-attr "description"
        tags-attr ["one", "two"]

        plaintext-password "HELLO-nuvla-69"

        href (str ct/resource-type "/" ct-hashed-pwd/method)
        template-url (str p/service-context ct/resource-type "/" ct-hashed-pwd/method)

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     :response
                     :body)

        create-no-href {:template (-> template
                                      ltu/strip-unwanted-attrs
                                      (assoc :password plaintext-password))}

        create-href {:name        name-attr
                     :description description-attr
                     :tags        tags-attr
                     :template    {:href     href
                                   :password plaintext-password}}]

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
                   :body (json/write-str create-no-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; create a credential as a normal user
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-href))
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
            (ltu/is-operation-present "edit")
            (ltu/is-operation-present "check-password")
            (ltu/is-operation-present "change-password")))

      ;; other users should not be able to see the credential
      (-> session-other
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags hash] :as cred} (-> session-user
                                                              (request abs-uri)
                                                              (ltu/body->edn)
                                                              (ltu/is-status 200)
                                                              :response
                                                              :body)]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is hash)

        ;; ensure that the check-password action works
        (let [op-url (-> session-user
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/get-op "check-password"))
              check-url (str p/service-context op-url)]

          (-> session-user
              (request check-url
                       :request-method :post
                       :body (json/write-str {:password plaintext-password}))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-user
              (request check-url
                       :request-method :post
                       :body (json/write-str {:password "WRONG_password_69"}))
              (ltu/body->edn)
              (ltu/is-status 403))

          ;; ensure that the change-password action works
          (let [new-password "GOODBYE-nuvla-96"
                op-url (-> session-user
                           (request abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/get-op "change-password"))
                change-pwd-url (str p/service-context op-url)]

            (-> session-user
                (request change-pwd-url
                         :request-method :post
                         :body (json/write-str {:current-password plaintext-password
                                                :new-password     new-password}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session-user
                (request check-url
                         :request-method :post
                         :body (json/write-str {:password new-password}))
                (ltu/body->edn)
                (ltu/is-status 200)))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


