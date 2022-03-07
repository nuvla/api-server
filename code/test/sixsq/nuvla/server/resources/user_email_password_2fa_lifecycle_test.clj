(ns sixsq.nuvla.server.resources.user-email-password-2fa-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [one-time.core :as ot]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [ring.util.codec :as codec]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.two-factor-auth.utils :as auth-2fa]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" email-password/resource-url)))


(deftest lifecycle-email
  (let [email-body    (atom nil)
        session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  ;; WARNING: This is a fragile Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (reset! email-body body)
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [href               (str user-tpl/resource-type "/" email-password/registration-method)

            description-attr   "description"
            tags-attr          ["one", "two"]
            plaintext-password "Plaintext-password-1"
            jane-email         "jane@example.org"

            href-create        {:description description-attr
                                :tags        tags-attr
                                :template    {:href     href
                                              :password plaintext-password
                                              :email    jane-email}}]

        ;; create user
        (let [resp                 (-> session-anon
                                       (request base-uri
                                                :request-method :post
                                                :body (json/write-str href-create))
                                       (ltu/body->edn)
                                       (ltu/is-status 201))
              user-id              (ltu/body-resource-id resp)
              user-url             (str p/service-context user-id)
              session-created-user (header session authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon"))

              enable-2fa-url       (-> session-created-user
                                       (request user-url)
                                       (ltu/body->edn)
                                       (ltu/is-operation-present :enable-2fa)
                                       (ltu/is-operation-absent :disable-2fa)
                                       (ltu/get-op-url :enable-2fa))

              validation-link      (->> @email-body second :content
                                        (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*")
                                        second)

              session-base-url     (str p/service-context session/resource-type)
              valid-session-create {:template {:href     (str st/resource-type "/password")
                                               :username jane-email
                                               :password plaintext-password}}]


          ;; user should provide method
          (-> session-created-user
              (request enable-2fa-url
                       :request-method :post
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/message-matches "resource does not satisfy defined schema")
              (ltu/is-status 400))

          ;; user should have a validated email
          (-> session-created-user
              (request enable-2fa-url
                       :request-method :post
                       :body (json/write-str {:method auth-2fa/method-email}))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/message-matches "User should have a validated email."))

          ;; check validation of resource
          (is (not (nil? validation-link)))

          (-> session-admin
              (request (str p/service-context user-id))
              (ltu/body->edn)
              (ltu/is-status 200))

          (is (re-matches #"^email.*successfully validated$" (-> session-anon
                                                                 (request validation-link)
                                                                 (ltu/body->edn)
                                                                 (ltu/is-status 200)
                                                                 (ltu/body)
                                                                 :message)))

          ;; user is able to get session without 2FA
          (-> session-anon
              (request session-base-url
                       :request-method :post
                       :body (json/write-str valid-session-create))
              (ltu/body->edn)
              (ltu/is-set-cookie)
              (ltu/is-status 201))

          (let [location (-> session-created-user
                             (request enable-2fa-url
                                      :request-method :post
                                      :body (json/write-str {:method auth-2fa/method-email}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/location))]

            ;; user should not be able to enable 2FA until callback get successfully activated
            (-> session-created-user
                (request user-url)
                (ltu/body->edn)
                (ltu/is-key-value :auth-method-2fa nil))


            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))
            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")
                  user-token        (->> @email-body second :content
                                         (re-find #"\d+"))]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data auth-2fa/method-email)
                  (ltu/is-key-value :token :data user-token))

              ; user should not be able to see callback data
              (-> session-created-user
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 403))

              ;; user should be able to execute callback multiple times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token user-token}))
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; user 2FA method should be set
              (-> session-created-user
                  (request user-url)
                  (ltu/body->edn)
                  (ltu/is-key-value :auth-method-2fa auth-2fa/method-email))

              ;; user should not be able to remove :auth-method by edit
              (-> session-created-user
                  (request (str user-url "?select=id,auth-method-2fa")
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :auth-method-2fa auth-2fa/method-email))))

          ;; create session should now return callback to validate token
          (let [location (-> session-anon
                             (request session-base-url
                                      :request-method :post
                                      :body (json/write-str valid-session-create))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/location))]
            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))

            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")
                  user-token        (->> @email-body second :content
                                         (re-find #"\d+"))]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data auth-2fa/method-email)
                  (ltu/is-key-value :token :data user-token))

              ; user should not be able to see callback data
              (-> session-created-user
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 403))

              ;; user should be able to execute callback multiple times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              ;; session is created when token is valid
              (let [session-url (-> session-anon
                                    (request callback-exec-url
                                             :request-method :put
                                             :body (json/write-str {:token user-token}))
                                    (ltu/body->edn)
                                    (ltu/is-set-cookie)
                                    (ltu/is-status 201)
                                    (ltu/location-url))]
                (-> session-admin
                    (request session-url)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :user user-id)
                    (ltu/is-key-value :identifier jane-email))

                ;; after a successful execution of callback, callback is no more executable
                (-> session-anon
                    (request callback-exec-url
                             :request-method :put
                             :body (json/write-str {:token user-token}))
                    (ltu/body->edn)
                    (ltu/is-status 409)
                    (ltu/message-matches "cannot re-execute callback")))))

          ;; create session will not be possible after 3 faillures, even with right token at 4th try
          (let [location (-> session-anon
                             (request session-base-url
                                      :request-method :post
                                      :body (json/write-str valid-session-create))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/location))]
            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))

            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")
                  user-token        (->> @email-body second :content
                                         (re-find #"\d+"))]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data auth-2fa/method-email)
                  (ltu/is-key-value :token :data user-token))

              ;; user should be able to execute callback 3 times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              ;; 4th try with right token will fail
              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token user-token}))
                  (ltu/body->edn)
                  (ltu/is-status 409))))

          ;; user should be able to disable 2fa
          (let [disable-2fa-url (-> session-created-user
                                    (request user-url)
                                    (ltu/body->edn)
                                    (ltu/is-operation-absent :enable-2fa)
                                    (ltu/is-operation-present :disable-2fa)
                                    (ltu/get-op-url :disable-2fa))
                location        (-> session-created-user
                                    (request disable-2fa-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/location))]

            ;; user should not be able to disable 2FA until callback get successfully activated
            (-> session-created-user
                (request user-url)
                (ltu/body->edn)
                (ltu/is-key-value :auth-method-2fa auth-2fa/method-email))


            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))
            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")
                  user-token        (->> @email-body second :content
                                         (re-find #"\d+"))]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data "none")
                  (ltu/is-key-value :token :data user-token))

              ; user should not be able to see callback data
              (-> session-created-user
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 403))

              ;; user should be able to execute callback multiple times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token user-token}))
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; user 2FA method should be set to none
              (-> session-created-user
                  (request user-url)
                  (ltu/body->edn)
                  (ltu/is-key-value :auth-method-2fa "none"))

              ;; user is re-able to get session without 2FA
              (-> session-anon
                  (request session-base-url
                           :request-method :post
                           :body (json/write-str valid-session-create))
                  (ltu/body->edn)
                  (ltu/is-set-cookie)
                  (ltu/is-status 201))
              ))
          ))
      )))


(deftest lifecycle-totp
  (let [email-body    (atom nil)
        session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  ;; WARNING: This is a fragile Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (reset! email-body body)
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [href               (str user-tpl/resource-type "/" email-password/registration-method)

            description-attr   "description"
            tags-attr          ["one", "two"]
            plaintext-password "Plaintext-password-1"
            tarzan-email       "tarzan@example.org"

            href-create        {:description description-attr
                                :tags        tags-attr
                                :template    {:href     href
                                              :password plaintext-password
                                              :email    tarzan-email}}]

        ;; create user
        (let [resp                 (-> session-anon
                                       (request base-uri
                                                :request-method :post
                                                :body (json/write-str href-create))
                                       (ltu/body->edn)
                                       (ltu/is-status 201))
              user-id              (ltu/body-resource-id resp)
              user-url             (str p/service-context user-id)
              session-created-user (header session authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon"))

              enable-2fa-url       (-> session-created-user
                                       (request user-url)
                                       (ltu/body->edn)
                                       (ltu/is-operation-present :enable-2fa)
                                       (ltu/is-operation-absent :disable-2fa)
                                       (ltu/get-op-url :enable-2fa))

              validation-link      (->> @email-body second :content
                                        (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*")
                                        second)

              session-base-url     (str p/service-context session/resource-type)
              valid-session-create {:template {:href     (str st/resource-type "/password")
                                               :username tarzan-email
                                               :password plaintext-password}}
              secret               (atom nil)
              get-totp             (comp str ot/get-totp-token)]


          ;; user should provide method
          (-> session-created-user
              (request enable-2fa-url
                       :request-method :post
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/message-matches "resource does not satisfy defined schema")
              (ltu/is-status 400))

          (is (re-matches #"^email.*successfully validated$" (-> session-anon
                                                                 (request validation-link)
                                                                 (ltu/body->edn)
                                                                 (ltu/is-status 200)
                                                                 (ltu/body)
                                                                 :message)))

          ;; user is able to get session without 2FA
          (-> session-anon
              (request session-base-url
                       :request-method :post
                       :body (json/write-str valid-session-create))
              (ltu/body->edn)
              (ltu/is-set-cookie)
              (ltu/is-status 201))

          (let [enable-resp (-> session-created-user
                                (request enable-2fa-url
                                         :request-method :post
                                         :body (json/write-str
                                                 {:method auth-2fa/method-totp}))
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/body))
                location    (:location enable-resp)]
            (reset! secret (:secret enable-resp))

            ;; user should not be able to enable 2FA until callback get successfully activated
            (-> session-created-user
                (request user-url)
                (ltu/body->edn)
                (ltu/is-key-value :auth-method-2fa nil))


            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))
            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data auth-2fa/method-totp))

              ; user should not be able to see callback data
              (-> session-created-user
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 403))

              ;; user should be able to execute callback multiple times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token (get-totp @secret)}))
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; user 2FA method should be set
              (-> session-created-user
                  (request user-url)
                  (ltu/body->edn)
                  (ltu/is-key-value :auth-method-2fa auth-2fa/method-totp)
                  (ltu/is-key-value (comp not str/blank?) :credential-totp
                                    true))

              ;; user should not be able to remove :auth-method by edit
              (-> session-created-user
                  (request (str user-url
                                "?select=id,auth-method-2fa,credential-totp")
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :auth-method-2fa auth-2fa/method-totp)
                  (ltu/is-key-value (comp not str/blank?) :credential-totp
                                    true))))

          ;; create session should now return callback to validate token
          (let [location (-> session-anon
                             (request session-base-url
                                      :request-method :post
                                      :body (json/write-str valid-session-create))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/location))]
            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))

            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")
                  user-token        (get-totp @secret)]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data auth-2fa/method-totp))

              ; user should not be able to see callback data
              (-> session-created-user
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 403))

              ;; user should be able to execute callback multiple times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              ;; session is created when token is valid
              (let [session-url (-> session-anon
                                    (request callback-exec-url
                                             :request-method :put
                                             :body (json/write-str {:token user-token}))
                                    (ltu/body->edn)
                                    (ltu/is-set-cookie)
                                    (ltu/is-status 201)
                                    (ltu/location-url))]
                (-> session-admin
                    (request session-url)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :user user-id)
                    (ltu/is-key-value :identifier tarzan-email))

                ;; after a successful execution of callback, callback is no more executable
                (-> session-anon
                    (request callback-exec-url
                             :request-method :put
                             :body (json/write-str {:token user-token}))
                    (ltu/body->edn)
                    (ltu/is-status 409)
                    (ltu/message-matches "cannot re-execute callback")))))

          ;; create session will not be possible after 3 faillures, even with right token at 4th try
          (let [location (-> session-anon
                             (request session-base-url
                                      :request-method :post
                                      :body (json/write-str valid-session-create))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/location))]
            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))

            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data auth-2fa/method-totp))

              ;; user should be able to execute callback 3 times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              ;; 4th try with right token will fail
              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token (get-totp @secret)}))
                  (ltu/body->edn)
                  (ltu/is-status 409))))

          ;; user should be able to disable 2fa
          (let [disable-2fa-url (-> session-created-user
                                    (request user-url)
                                    (ltu/body->edn)
                                    (ltu/is-operation-absent :enable-2fa)
                                    (ltu/is-operation-present :disable-2fa)
                                    (ltu/get-op-url :disable-2fa))
                location        (-> session-created-user
                                    (request disable-2fa-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/location))]

            ;; user should not be able to disable 2FA until callback get successfully activated
            (-> session-created-user
                (request user-url)
                (ltu/body->edn)
                (ltu/is-key-value :auth-method-2fa auth-2fa/method-totp))


            (is (re-matches #"http.*\/api\/callback\/.*\/execute" location))
            (let [callback-url      (->> location
                                         codec/url-decode
                                         (re-matches #"http.*(\/api.*)\/execute")
                                         second)
                  callback-exec-url (str callback-url "/execute")]

              (-> session-admin
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :method :data "none"))

              ; user should not be able to see callback data
              (-> session-created-user
                  (request callback-url)
                  (ltu/body->edn)
                  (ltu/is-status 403))

              ;; user should be able to execute callback multiple times
              (-> session-created-user
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token "wrong"}))
                  (ltu/body->edn)
                  (ltu/is-status 400)
                  (ltu/message-matches auth-2fa/msg-wrong-2fa-token))

              (-> session-anon
                  (request callback-exec-url
                           :request-method :put
                           :body (json/write-str {:token (get-totp @secret)}))
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; user 2FA method should be set to none
              (-> session-created-user
                  (request user-url)
                  (ltu/body->edn)
                  (ltu/is-key-value :auth-method-2fa "none"))

              ;; user is re-able to get session without 2FA
              (-> session-anon
                  (request session-base-url
                           :request-method :post
                           :body (json/write-str valid-session-create))
                  (ltu/body->edn)
                  (ltu/is-set-cookie)
                  (ltu/is-status 201))
              ))
          ))
      )))
