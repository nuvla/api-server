(ns sixsq.nuvla.server.resources.credential-hashed-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as ct-hashed-password]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ct-hashed-password/resource-url)))


(deftest lifecycle

  (let [session            (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
        session-admin      (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user       (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-other      (header session authn-info-header "user/tarzan user/tarzan group/nuvla-user group/nuvla-anon")
        session-anon       (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        name-attr          "name"
        description-attr   "description"
        tags-attr          ["one", "two"]

        plaintext-password "HELLO-nuvla-69"

        href               (str ct/resource-type "/" ct-hashed-password/method)
        template-url       (str p/service-context ct/resource-type "/" ct-hashed-password/method)

        template           (-> session-admin
                               (request template-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))

        create-no-href     {:template (-> template
                                          ltu/strip-unwanted-attrs
                                          (assoc :password plaintext-password))}

        create-href        {:name        name-attr
                            :description description-attr
                            :tags        tags-attr
                            :template    {:href     href
                                          :password plaintext-password}}
        authn-info-admin   {:user-id      "group/nuvla-admin"
                            :active-claim "group/nuvla-admin"
                            :claims       ["group/nuvla-admin" "group/nuvla-anon" "group/nuvla-user"]}
        authn-info-jane    {:user-id      "user/jane"
                            :active-claim "user/jane"
                            :claims       ["group/nuvla-anon" "user/jane" "group/nuvla-user"]}
        authn-info-anon    {:user-id      "user/unknown"
                            :active-claim "user/unknown"
                            :claims       #{"user/unknown" "group/nuvla-anon"}}]

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
    (doseq [[session event-owners authn-info]
            [[session-admin ["group/nuvla-admin"] authn-info-admin]
             [session-user ["group/nuvla-admin"] authn-info-jane]
             [session-anon ["group/nuvla-admin"] authn-info-anon]]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-no-href))
          (ltu/body->edn)
          (ltu/is-status 400))

      (ltu/is-last-event nil
                         {:name               "credential.add"
                          :description        "credential.add attempt failed."
                          :category           "add"
                          :success            false
                          :linked-identifiers []
                          :authn-info         authn-info
                          :acl                {:owners event-owners}}))

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; create a credential as a normal user
    (let [resp    (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str create-href))
                      (ltu/body->edn)
                      (ltu/is-status 201))
          id      (ltu/body-resource-id resp)
          uri     (-> resp
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      (ltu/is-last-event uri
                         {:name               "credential.add"
                          :description        (str "user/jane added credential " name-attr ".")
                          :category           "add"
                          :success            true
                          :linked-identifiers []
                          :authn-info         authn-info-jane
                          :acl                {:owners ["group/nuvla-admin" "user/jane"]}})

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)
            (ltu/is-operation-present :check-password)
            (ltu/is-operation-present :change-password)))

      ;; other users should not be able to see the credential
      (-> session-other
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags hash]} (-> session-user
                                                     (request abs-uri)
                                                     (ltu/body->edn)
                                                     (ltu/is-status 200)
                                                     (ltu/body))]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is hash)

        ;; ensure that the check-password action works
        (let [op-url    (-> session-user
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
          (let [new-password   "GOODBYE-nuvla-96"
                op-url         (-> session-user
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
          (ltu/is-status 200))

      (ltu/is-last-event uri
                         {:name               "credential.delete"
                          :description        (str "user/jane deleted credential " name-attr ".")
                          :category           "delete"
                          :success            true
                          :linked-identifiers []
                          :authn-info         authn-info-jane
                          :acl                {:owners ["group/nuvla-admin" "user/jane"]}}))))


