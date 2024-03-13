(ns sixsq.nuvla.server.resources.user-email-invitation-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.sending :as email-sending]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-invitation :as email-invitation]
    [sixsq.nuvla.server.util.general :as gen-util]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" email-invitation/resource-url)))


(deftest lifecycle
  (let [invitation-link (atom nil)

        template-url    (str p/service-context user-tpl/resource-type "/" email-invitation/registration-method)

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

                  ;; WARNING: This is a fragile! Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (let [url (->> body second :content
                                                       (re-matches #"(?s).*link:\n\n\s+(.*?)\n.*")
                                                       second)]
                                          (reset! invitation-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [template          (-> session-admin
                                  (request template-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/body))

            href              (str user-tpl/resource-type "/" email-invitation/registration-method)

            description-attr  "description"
            tags-attr         ["one", "two"]

            no-href-create    {:template (ltu/strip-unwanted-attrs (assoc template :email "alice@example.org"))}
            href-create       {:description description-attr
                               :tags        tags-attr
                               :template    {:href         href
                                             :email        "jane@example.org"
                                             :redirect-url "http://redirect.example.org"}}

            invalid-create    (assoc-in href-create [:template :href] "user-template/unknown-template")

            bad-params-create (assoc-in href-create [:template :invalid] "BAD")]


        ;; user collection query should succeed but be empty for all users
        (doseq [session [session-anon session-user session-admin]]
          (-> session
              (content-type "application/x-www-form-urlencoded")
              (request base-uri
                       :request-method :put
                       :body (codec/form-encode {:filter "name!='super'"}))
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


        ;; create with user anon fails
        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str href-create))
            (ltu/body->edn)
            (ltu/is-status 400))

        ;; create user
        (with-redefs [auth-password/invited-by (fn [_] "tarzan")]
          (let [resp                 (-> session-user
                                        (request base-uri
                                                 :request-method :post
                                                 :body (json/write-str href-create))
                                        (ltu/body->edn)
                                        (ltu/is-status 201))
               user-id              (ltu/body-resource-id resp)
               session-created-user (header session authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon"))

               {email-id :email :as user} (-> session-created-user
                                              (request (str p/service-context user-id))
                                              (ltu/body->edn)
                                              (ltu/body))

               callback-url         (->> @invitation-link
                                         gen-util/decode-uri-component
                                         (re-matches #".*callback=(.*?)&.*")
                                         second)]

           ;; verify name attribute (should default to username if no :name)
           (is (= "jane@example.org" (:name user)))


           ; 1 identifier is visible for the created user one for email
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
           (is (not (nil? @invitation-link)))

           (-> session-admin
               (request (str p/service-context user-id))
               (ltu/body->edn)
               (ltu/is-status 200))

           (-> session-anon
               (request callback-url
                        :request-method :post
                        :body (json/write-str {:new-password "VeryDifficult-1"}))
               (ltu/body->edn)
               (ltu/is-status 200)
               (ltu/message-matches
                 (re-pattern (format "set password for %s successfully executed" user-id))))

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

           ;; user can delete his account
           (-> session-created-user
               (request (str p/service-context user-id)
                        :request-method :delete)
               (ltu/body->edn)
               (ltu/is-status 200))

           (-> session-created-user
               (request (str p/service-context user-identifier/resource-type))
               (ltu/body->edn)
               (ltu/is-status 200)
               (ltu/is-count 0))

           (-> session-created-user
               (request (str p/service-context email-id))
               (ltu/body->edn)
               (ltu/is-status 404))))))))
