(ns sixsq.nuvla.server.resources.user-email-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" email-password/resource-url)))


(deftest lifecycle
  (let [validation-link (atom nil)

        template-url (str p/service-context user-tpl/resource-type "/" email-password/registration-method)

        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon (header session authn-info-header "user/unknown group/nuvla-anon")]

    (with-redefs [email-utils/smtp-cfg (fn []
                                         {:host "smtp@example.com"
                                          :port 465
                                          :ssl  true
                                          :user "admin"
                                          :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body] :as message}]
                                        (let [url (second (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*" body))]
                                          (reset! validation-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [template (-> session-admin
                         (request template-url)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (get-in [:response :body]))


            href (str user-tpl/resource-type "/" email-password/registration-method)

            name-attr "name"
            description-attr "description"
            tags-attr ["one", "two"]
            plaintext-password "Plaintext-password-1"

            uname-alt "user/jane"

            no-href-create {:template (ltu/strip-unwanted-attrs (assoc template
                                                                  :password plaintext-password
                                                                  :password-repeated plaintext-password
                                                                  :username "alice"
                                                                  :email "alice@example.org"))}
            href-create {:name        name-attr
                         :description description-attr
                         :tags        tags-attr
                         :template    {:href              href
                                       :password          plaintext-password
                                       :password-repeated plaintext-password
                                       :username          "user/jane"
                                       :email             "jane@example.org"}}

            href-create-alt (assoc-in href-create [:template :username] uname-alt)

            href-create-redirect (assoc-in href-create-alt [:template :redirectURI] "http://redirect.example.org")

            invalid-create (assoc-in href-create [:template :href] "user-template/unknown-template")

            bad-params-create (assoc-in href-create [:template :invalid] "BAD")
            bad-params-create-redirect (assoc-in href-create-redirect [:template :invalid] "BAD")]


        ;; user collection query should succeed but be empty for all users
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count zero?)
              (ltu/is-operation-present "add")
              (ltu/is-operation-absent "delete")
              (ltu/is-operation-absent "edit")))

        ;; create a new user; fails without reference
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (json/write-str no-href-create))
              (ltu/body->edn)
              (ltu/is-status 400)))

        ;; create with invalid template fails
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (json/write-str invalid-create))
              (ltu/body->edn)
              (ltu/is-status 404)))

        ;; create with bad parameters fails
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (json/write-str bad-params-create))
              (ltu/body->edn)
              (ltu/is-status 400)))

        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (json/write-str bad-params-create-redirect))
              (ltu/body->edn)
              (ltu/is-status 303)))


        ;; create user
        (let [resp (-> session-anon
                       (request base-uri
                                :request-method :post
                                :body (json/write-str href-create))
                       (ltu/body->edn)
                       (ltu/is-status 201))
              user-id (get-in resp [:response :body :resource-id])
              session-created-user (header session authn-info-header (str user-id " group/nuvla-user group/nuvla-anon"))]

          (let [{credential-id :credential-password,
                 email-id      :email :as user} (-> session-created-user
                                                    (request (str p/service-context user-id))
                                                    (ltu/body->edn)
                                                    (get-in [:response :body]))]
            ; credential password is created and visible by the created user
            (-> session-created-user
                (request (str p/service-context credential-id))
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session-user
                (request (str p/service-context credential-id))
                (ltu/body->edn)
                (ltu/is-status 403))

            ; 2 identifier are visible for the created user one for email and another one for the username
            (-> session-created-user
                (request (str p/service-context user-identifier/resource-type))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 2))

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
                                                                   :response
                                                                   :body
                                                                   :message)))

            (let [{:keys [state] :as user} (-> session-created-user
                                               (request (str p/service-context user-id))
                                               (ltu/body->edn)
                                               :response
                                               :body)]
              (is (= "ACTIVE" state)))

            (let [{:keys [validated] :as email} (-> session-created-user
                                                    (request (str p/service-context email-id))
                                                    (ltu/body->edn)
                                                    :response
                                                    :body)]
              (is validated))

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
                (ltu/is-status 404)))
          )

        ;; create user if fail with creation of child resources are cleaned up
        (let [resp (-> session-anon
                       (request base-uri
                                :request-method :post
                                :body (json/write-str
                                        (assoc-in href-create
                                                  [:template :username] "jane@example.org")))
                       (ltu/body->edn)
                       (ltu/is-status 409))
              user-id (get-in resp [:response :body :resource-id])
              session-created-user (header session authn-info-header (str user-id " group/nuvla-user group/nuvla-anon"))]

          (let [{:keys [credential-password, email] :as user} (-> session-created-user
                                                                  (request (str p/service-context user-id))
                                                                  (ltu/body->edn)
                                                                  (get-in [:response :body]))]
            ; credential cleanup
            (-> session-admin
                (request (str p/service-context credential/resource-type))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 0))

            ; identifier cleanup
            (-> session-admin
                (request (str p/service-context user-identifier/resource-type))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 0))

            ; email cleanup
            (-> session-admin
                (request (str p/service-context email/resource-type))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 0))

            (-> session-created-user
                (request (str p/service-context email))
                (ltu/body->edn)
                (ltu/is-status 404))))

        ;; create user with redirect
        (let [resp (-> session-anon
                       (request base-uri
                                :request-method :post
                                :body (json/write-str href-create-redirect))
                       (ltu/body->edn)
                       (ltu/is-status 303))
              user-id (get-in resp [:response :body :resource-id])
              session-created-user (header session authn-info-header (str user-id " group/nuvla-user group/nuvla-anon"))
              uri (-> resp
                      (ltu/location))]
          (is user-id)
          (is (= "http://redirect.example.org" uri))

          (let [{:keys [credential-password, email] :as user} (-> session-created-user
                                                                  (request (str p/service-context user-id))
                                                                  (ltu/body->edn)
                                                                  (get-in [:response :body]))]
            ; credential password is created and visible by the created user
            (-> session-created-user
                (request (str p/service-context credential-password))
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session-user
                (request (str p/service-context credential-password))
                (ltu/body->edn)
                (ltu/is-status 403))

            ; 2 identifier are visible for the created user one for email and another one for the username
            (-> session-created-user
                (request (str p/service-context user-identifier/resource-type))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 2))

            ; one email is visible for the user
            (-> session-created-user
                (request (str p/service-context email))
                (ltu/body->edn)
                (ltu/is-status 200))

            ;user can delete his account
            (-> session-created-user
                (request (str p/service-context user-id)
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session-created-user
                (request (str p/service-context credential-password))
                (ltu/body->edn)
                (ltu/is-status 404))

            ; 2 identifier are visible for the created user one for email and another one for the username
            (-> session-created-user
                (request (str p/service-context user-identifier/resource-type))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 0))

            ; one email is visible for the user
            (-> session-created-user
                (request (str p/service-context email))
                (ltu/body->edn)
                (ltu/is-status 404))))
        ))))
