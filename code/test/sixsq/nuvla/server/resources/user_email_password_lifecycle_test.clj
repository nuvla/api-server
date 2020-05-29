(ns sixsq.nuvla.server.resources.user-email-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.customer :as customer]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.pricing :as pricing]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]
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

        template-url    (str p/service-context user-tpl/resource-type "/" email-password/registration-method)

        session         (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
        session-admin   (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user    (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon    (header session authn-info-header "user/unknown group/nuvla-anon")]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (let [url (second (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*" body))]
                                          (reset! validation-link url))
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
              (request base-uri)
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


        ;; create user
        (let [resp                 (-> session-anon
                                       (request base-uri
                                                :request-method :post
                                                :body (json/write-str href-create))
                                       (ltu/body->edn)
                                       (ltu/is-status 201))
              user-id              (ltu/body-resource-id resp)
              session-created-user (header session authn-info-header (str user-id " group/nuvla-user group/nuvla-anon"))

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
                       :body (json/write-str href-create))
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

          )))))


(deftest lifecycle-with-customer
  (let [session            (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
        session-admin      (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon       (header session authn-info-header "user/unknown group/nuvla-anon")

        href               (str user-tpl/resource-type "/" email-password/registration-method)

        plaintext-password "Plaintext-password-1"

        customer           {:fullname     "toto"
                            :address      {:street-address "Av. quelque chose"
                                           :city           "Meyrin"
                                           :country        "CH"
                                           :postal-code    "1217"}
                            :subscription {:plan-id       "plan_HGQ9iUgnz2ho8e"
                                           :plan-item-ids ["plan_HGQIIWmhYmi45G"
                                                           "plan_HIrgmGboUlLqG9"
                                                           "plan_HGQAXewpgs9NeW"
                                                           "plan_HGQqB0p8h86Ija"]}}

        tmpl               {:template {:href     href
                                       :password plaintext-password
                                       :email    "alex@example.org"
                                       :customer customer}}]

    (-> session-admin
        (request (str p/service-context pricing/resource-type)
                 :request-method :post
                 :body (json/write-str {}))
        (ltu/body->edn)
        (ltu/is-status 201))


    ;; create user
    (let [resp         (-> session-anon
                           (request base-uri
                                    :request-method :post
                                    :body (json/write-str tmpl))
                           (ltu/body->edn)
                           (ltu/is-status 201))
          user-id      (ltu/body-resource-id resp)
          session-user (header session authn-info-header (str user-id " group/nuvla-user group/nuvla-anon"))]

      ; credential password is created and visible by the created user
      (-> session-user
          (request (str p/service-context user-id))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body))

      (-> session-user
          (request (str p/service-context user-id)
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ; 1 customer is visible for the created user
      (doseq [{:keys [customer-id]} (-> session-user
                                        (request (str p/service-context customer/resource-type))
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-count 1)
                                        (ltu/entries))]
        (-> customer-id
            stripe/retrieve-customer
            stripe/delete-customer)))))
