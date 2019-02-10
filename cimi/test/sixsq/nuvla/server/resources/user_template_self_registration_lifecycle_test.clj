(ns sixsq.nuvla.server.resources.user-template-self-registration-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [postal.core :as postal]
    [ring.util.codec :as codec]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as ut]
    [sixsq.nuvla.server.resources.user-template-self-registration :as self]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context user/resource-type))

(def user-template-base-uri (str p/service-context ut/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str ut/resource-type "-" self/resource-url)))


(deftest lifecycle
  (let [validation-link (atom nil)]
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

      (let [uname "120720737412_eduid_chhttps___eduid_ch"
            uname-alt "120720737412_eduid_chhttps___eduid_ch_2"
            href (str ut/resource-type "/" self/registration-method)
            template-url (str p/service-context ut/resource-type "/" self/registration-method)

            session (-> (ltu/ring-app)
                        session
                        (content-type "application/json"))
            session-admin (header session authn-info-header "root ADMIN")
            session-user (header session authn-info-header (format "%s USER ANON" uname))
            session-anon (header session authn-info-header "unknown ANON")
            session-anon-form (-> session-anon
                                  (content-type u/form-urlencoded))]

        ;; create the self-registration user template
        ;; this is no longer added automatically to the database
        (let [user-template (->> {:group  "my-group"
                                  :icon   "some-icon"
                                  :order  10
                                  :hidden false}
                                 (merge self/resource)
                                 json/write-str)]

          (-> session-admin
              (request user-template-base-uri
                       :request-method :post
                       :body user-template)
              (ltu/is-status 201))

          (-> session-anon
              (request template-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (get-in [:response :body])))

        (let [template (-> session-admin
                           (request template-url)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (get-in [:response :body]))

              name-attr "name"
              description-attr "description"
              tags-attr ["one", "two"]
              plaintext-password "plaintext-password"

              no-href-create {:template (ltu/strip-unwanted-attrs (assoc template
                                                                    :username uname
                                                                    :password plaintext-password
                                                                    :passwordRepeat plaintext-password
                                                                    :emailAddress "user@example.org"))}
              href-create {:name        name-attr
                           :description description-attr
                           :tags        tags-attr
                           :template    {:href           href
                                         :username       uname
                                         :password       plaintext-password
                                         :passwordRepeat plaintext-password
                                         :emailAddress   "jane@example.org"}}

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


          ;; create user with redirect
          (let [resp (-> session-anon
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str href-create-redirect))
                         (ltu/body->edn)
                         (ltu/is-status 303))
                id (get-in resp [:response :body :resource-id])
                uri (-> resp
                        (ltu/location))]
            (is (= id (str "user/" uname-alt)))
            (is (= "http://redirect.example.org" uri)))

          ;; recreating the same user should fail (also verifies that user was created)
          (-> session-anon
              (request base-uri
                       :request-method :post
                       :body (json/write-str href-create-alt))
              (ltu/body->edn)
              (ltu/is-status 409))

          ;; create a user anonymously
          (let [resp (-> session-anon
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str href-create))
                         (ltu/body->edn)
                         (ltu/is-status 201))
                id (get-in resp [:response :body :resource-id])
                uri (-> resp
                        (ltu/location))
                abs-uri (str p/service-context uri)]

            ;; creating same user a second time should fail
            (-> session-anon
                (request base-uri
                         :request-method :post
                         :body (json/write-str href-create))
                (ltu/body->edn)
                (ltu/is-status 409))

            ;; user and admin should be able to see, edit, and delete user
            (doseq [session [session-user session-admin]]
              (-> session
                  (request abs-uri)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-operation-present "delete")
                  (ltu/is-operation-present "edit")))

            ;; check contents of resource
            (let [{:keys [name
                          description
                          tags
                          isSuperUser
                          state
                          deleted] :as user} (-> session-admin
                                                 (request abs-uri)
                                                 (ltu/body->edn)
                                                 :response
                                                 :body)]
              (is (= name name-attr))
              (is (= description description-attr))
              (is (= tags tags-attr))
              (is (false? isSuperUser))
              (is (= user/initial-state state))
              (is (false? deleted)))

            ;; edit
            (let [body (-> session-admin
                           (request abs-uri)
                           (ltu/body->edn)
                           :response
                           :body)
                  user-json (json/write-str (assoc body :isSuperUser true))]

              ;; anon users can NOT edit
              (-> session-anon
                  (request abs-uri
                           :request-method :put
                           :body user-json)
                  (ltu/is-status 403))

              ;; regular users can NOT set isSuperUser
              (-> session-user
                  (request abs-uri
                           :request-method :put
                           :body user-json)
                  (ltu/is-status 200))
              (is (false? (-> session-user
                              (request abs-uri)
                              (ltu/body->edn)
                              :response
                              :body
                              :isSuperUser)))

              ;; admin can set isSuperUser
              (-> session-admin
                  (request abs-uri
                           :request-method :put
                           :body user-json)
                  (ltu/body->edn)
                  (ltu/is-status 200))
              (is (true? (-> session-admin
                             (request abs-uri)
                             (ltu/body->edn)
                             :response
                             :body
                             :isSuperUser))))

            ;; check validation of resource
            (is (not (nil? @validation-link)))
            (is (re-matches #"^email.*successfully validated$" (-> session-anon
                                                                   (request @validation-link)
                                                                   (ltu/body->edn)
                                                                   (ltu/is-status 200)
                                                                   :response
                                                                   :body
                                                                   :message)))

            (let [{:keys [state] :as user} (-> session-admin
                                               (request abs-uri)
                                               (ltu/body->edn)
                                               :response
                                               :body)]
              (is (= "ACTIVE" state)))

            ;; admin can delete resource
            (-> session-admin
                (request abs-uri
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200)))

          ;; check that a form-encoded create request works
          (let [resp (-> session-anon-form
                         (request base-uri
                                  :request-method :post
                                  :body (codec/form-encode {:href href}))
                         (ltu/body->edn)
                         (ltu/is-status 201))

                uri (-> resp ltu/location)
                abs-uri (str p/service-context uri)]

            ;; check resource exists
            (-> session-admin
                (request abs-uri)
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; admin can delete resource
            (-> session-admin
                (request abs-uri
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200))))))))
