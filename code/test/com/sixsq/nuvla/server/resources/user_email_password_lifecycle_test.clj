(ns com.sixsq.nuvla.server.resources.user-email-password-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.email :as email]
    [com.sixsq.nuvla.server.resources.email.sending :as email-sending]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.user :as user]
    [com.sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [com.sixsq.nuvla.server.resources.user-template :as user-tpl]
    [com.sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" email-password/resource-url)))


(deftest lifecycle
  (let [validation-link (atom nil)

        template-url    (str p/service-context user-tpl/resource-type "/" email-password/registration-method)

        session         (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
        session-admin   (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user    (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon    (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

    (with-redefs [email-sending/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  postal/send-message (fn [_ msg]
                                        (reset! validation-link (ltu/extract-msg-callback-url msg))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [template           (-> session-admin
                                   (request template-url)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/body))

            href               (str user-tpl/resource-type "/" email-password/registration-method)

            description-attr   "description"
            tags-attr          ["one", "two"]
            plaintext-password "Plaintext-password-1"

            no-href-create     {:template (ltu/strip-unwanted-attrs (assoc template
                                                                      :password plaintext-password
                                                                      :username "alice"
                                                                      :email "alice@example.org"))}
            href-create        {:description description-attr
                                :tags        tags-attr
                                :template    {:href     href
                                              :password plaintext-password
                                              ;:username "user/jane"
                                              :email    "jane@example.org"}}

            invalid-create     (assoc-in href-create [:template :href] "user-template/unknown-template")

            bad-params-create  (assoc-in href-create [:template :invalid] "BAD")]


        ;; user collection query should succeed but be empty for all users
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request (str base-uri "?filter=name!='super'"))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count zero?)
              (ltu/is-operation-present :add)
              (ltu/is-operation-absent :delete)
              (ltu/is-operation-absent :edit)))

        ;; create a new user; fails without reference
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (j/write-value-as-string no-href-create))
              (ltu/body->edn)
              (ltu/is-status 400)))

        ;; create with invalid template fails
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (j/write-value-as-string invalid-create))
              (ltu/body->edn)
              (ltu/is-status 404)))

        ;; create with bad parameters fails
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (j/write-value-as-string bad-params-create))
              (ltu/body->edn)
              (ltu/is-status 400)))


        ;; create user
        (let [resp                 (-> session-anon
                                       (request base-uri
                                                :request-method :post
                                                :body (j/write-value-as-string href-create))
                                       (ltu/body->edn)
                                       (ltu/is-status 201))
              user-id              (ltu/body-resource-id resp)
              session-created-user (header session authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon"))

              {credential-id :credential-password,
               email-id      :email :as user} (-> session-created-user
                                                  (request (str p/service-context user-id))
                                                  (ltu/body->edn)
                                                  (ltu/body))]

          ;; verify name attribute (should default to username if no :name)
          (is (= "jane@example.org" (:name user)))

          ; credential password is created and visible by the created user
          (-> session-created-user
              (request (str p/service-context credential-id))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-user
              (request (str p/service-context credential-id))
              (ltu/body->edn)
              (ltu/is-status 403))

          ; user should not be able to change his state
          (-> session-created-user
              (request (str p/service-context user-id)
                       :request-method :put
                       :body (j/write-value-as-string {:state "SUSPENDED"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "NEW"))

          ; 1 identifier is visible for the created user one for email (username was not provided)
          (-> session-created-user
              (request (str p/service-context user-identifier/resource-type))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 1))

          ; one email is visible for the user
          (-> session-created-user
              (request (str p/service-context email-id))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; check validation of resource
          (is (not (nil? @validation-link)))

          (-> session-admin
              (request (str p/service-context user-id))
              (ltu/body->edn)
              (ltu/is-status 200))

          (is (re-matches #"^email.*successfully validated$" (-> session-anon
                                                                 (request @validation-link)
                                                                 (ltu/body->edn)
                                                                 (ltu/is-status 200)
                                                                 (ltu/body)
                                                                 :message)))

          (let [{:keys [state]} (-> session-created-user
                                    (request (str p/service-context user-id))
                                    (ltu/body->edn)
                                    (ltu/body))]
            (is (= "ACTIVE" state)))

          (let [{:keys [validated]} (-> session-created-user
                                        (request (str p/service-context email-id))
                                        (ltu/body->edn)
                                        (ltu/body))]
            (is validated))

          ;; user can't create a user with an existing identifier

          (-> session-anon
              (request base-uri
                       :request-method :post
                       :body (j/write-value-as-string href-create))
              (ltu/body->edn)
              (ltu/is-status 409)
              (ltu/message-matches #"Account with identifier \".*\" already exist!"))

          ;; user can delete his account
          (-> session-created-user
              (request (str p/service-context user-id)
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-created-user
              (request (str p/service-context credential-id))
              (ltu/body->edn)
              (ltu/is-status 404))

          (-> session-created-user
              (request (str p/service-context user-identifier/resource-type))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 0))

          (-> session-created-user
              (request (str p/service-context email-id))
              (ltu/body->edn)
              (ltu/is-status 404))

          ;; ensure that user is not created and all child resources are cleaned up
          ;; admin credential dependent of execution speed this is why <= 1

          (-> session-admin
              (request (str p/service-context credential/resource-type))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count #(<= %1 1)))

          ; identifier cleanup
          (-> session-admin
              (request (str p/service-context user-identifier/resource-type "?filter=identifier!='super'"))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 0))

          ; email cleanup
          (-> session-admin
              (request (str p/service-context email/resource-type))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 0))

          )))))

