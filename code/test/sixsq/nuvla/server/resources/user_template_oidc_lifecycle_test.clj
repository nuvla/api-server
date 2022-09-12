(ns sixsq.nuvla.server.resources.user-template-oidc-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.oidc :as auth-oidc]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.callback.utils :as cbu]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as ut]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [sixsq.nuvla.server.resources.user-template-oidc :as oidc]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(def configuration-base-uri (str p/service-context configuration/resource-type))


(def user-template-base-uri (str p/service-context ut/resource-type))


(def ^:const hook-pattern #".*/api/hook/oidc-user/oidc")

(def auth-pubkey
  (str
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA835H7CQt2oOmlj6GoZp+"
    "dFLE6k43Ybi3ku/yuuzatlnet95xVibbyD+DWBz8owRx5F7dZKbFuJPD7KNZWnxD"
    "P4hSO6p7xg6xOjWrU2naMW8SaWs8cbU7rssRKbEmCc39888pgNi6/VgZiHXmVeUR"
    "eWbxlrppIhIrRiHwf8LHA0LzGn0UAS4K0dMPdRR02vWs5hRw8yOAr0hXU2LUb7AO"
    "uP73cumiWDqkmJBhKa1PYN7vixkud1Gb1UhJ77N+W32VdOOXbiS4cophQkfdNhjk"
    "jVunw8YkO7dsBhVP/8bqLDLw/8NsSAKwlzsoNKbrjVQ/NmHMJ88QkiKwv+E6lidy"
    "3wIDAQAB"))


(def configuration-user-oidc
  {:template {:service       "session-oidc"                 ;;reusing configuration from session
              :instance      oidc/registration-method
              :client-id     "FAKE_CLIENT_ID"
              :client-secret "MyClientSecret"
              :authorize-url "https://authorize.oidc.com/authorize"
              :token-url     "https://token.oidc.com/token"
              :jwks-url      "https://jwks.oidc.com"}})


(deftest check-metadata
  (mdtu/check-metadata-exists (str ut/resource-type "-" oidc/resource-url)
                              (str ut/resource-type "-" oidc/resource-url "-create")))


(deftest lifecycle

  (let [href          (str ut/resource-type "/" oidc/registration-method)
        template-url  (str p/service-context ut/resource-type "/" oidc/registration-method)

        session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json")
                          (header authn-info/authn-info-header "user/unknown user/unknown group/nuvla-anon"))
        session-admin (header session-anon authn-info/authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info/authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    ;; must create the oidc user template; this is not created automatically
    (let [user-template (->> {:group  "my-group"
                              :icon   "some-icon"
                              :order  10
                              :hidden false}
                             (merge oidc/resource)
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
          (ltu/body)))

    ;; get user template so that user resources can be tested
    (let [name-attr        "name"
          description-attr "description"
          tags-attr        ["a-1" "b-2"]

          href-create      {:name        name-attr
                            :description description-attr
                            :tags        tags-attr
                            :template    {:href href}}]

      ;; anonymous query should succeed but have no entries
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      ;; configuration must have OIDC client id and base URL, if not should get 500
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str href-create))
          (ltu/body->edn)
          (ltu/message-matches #".*missing or incorrect configuration.*")
          (ltu/is-status 500))

      ;; anonymous create must succeed (normal create and href create)
      (let [;;
            ;; create the session-oidc configuration to use for these tests
            ;;
            cfg-href (-> session-admin
                         (request configuration-base-uri
                                  :request-method :post
                                  :body (json/write-str configuration-user-oidc))
                         (ltu/body->edn)
                         (ltu/is-status 201)
                         (ltu/location))
            _        (-> session-admin
                         (request configuration-base-uri
                                  :request-method :post
                                  :body (json/write-str configuration-user-oidc))
                         (ltu/body->edn)
                         (ltu/is-status 409))]



        (is (= cfg-href (str "configuration/session-oidc-" oidc/registration-method)))

        (let [uri (-> session-anon
                      (request base-uri
                               :request-method :post
                               :body (json/write-str href-create))
                      (ltu/body->edn)
                      (ltu/is-status 303)
                      ltu/location)]

          ;; redirect URLs in location header should contain the client ID and resource id
          (is (re-matches #".*FAKE_CLIENT_ID.*" (or uri "")))
          (is (re-matches hook-pattern (or uri "")))

          ;; anonymous, user and admin query should succeed but have no users
          (doseq [session [session-anon session-user session-admin]]
            (-> session
                (request base-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count zero?)))

          ;; validate hooks
          (let [get-redirect-uri #(->> % (re-matches #".*redirect_uri=(.*)$") second)

                validate-url     (get-redirect-uri uri)]

            ;; remove the authentication configuration
            (-> session-admin
                (request (str p/service-context cfg-href)
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; add the configuration back again
            (-> session-admin
                (request configuration-base-uri
                         :request-method :post
                         :body (json/write-str configuration-user-oidc))
                (ltu/body->edn)
                (ltu/is-status 201))

            (-> session-anon
                (request validate-url)
                (ltu/body->edn)
                (ltu/message-matches #".*not contain required code.*")
                (ltu/is-status 303))

            ;; try now with a fake code

            (doseq [user-number (range 3)]

              (let [username    (str "OIDC_group/nuvla-user_" user-number)
                    email       (format "user-%s@example.com" user-number)
                    good-claims {:sub         username
                                 :email       email
                                 :given_name  "John"
                                 :family_name "Smith"
                                 :entitlement ["alpha-entitlement"]
                                 :realm       "my-realm"}
                    good-token  (sign/sign-cookie-info good-claims)
                    bad-claims  {}
                    bad-token   (sign/sign-cookie-info bad-claims)]

                (with-redefs [auth-oidc/get-id-token   (fn [_ _ _ oauth-code _]
                                                         (case oauth-code
                                                           "GOOD" good-token
                                                           "BAD" bad-token
                                                           nil))
                              auth-oidc/get-public-key (constantly auth-pubkey)]
                  (-> session-anon
                      (request (str validate-url "?code=NONE")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*unable to retrieve OIDC/MITREid access token.*")
                      (ltu/is-status 303))

                  (-> session-anon
                      (request (str validate-url "?code=BAD")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*OIDC/MITREid token is missing subject.*")
                      (ltu/is-status 303))

                  (is (nil? (uiu/user-identifier->user-id :oidc oidc/registration-method username)))

                  (-> session-anon
                      (request (str validate-url "?code=GOOD")
                               :request-method :get)
                      (ltu/is-status 303))

                  (let [user-id     (uiu/user-identifier->user-id :oidc oidc/registration-method username)
                        name-value  (uiu/generate-identifier :oidc oidc/registration-method username)
                        user-record (ex/get-user user-id)]

                    (is (not (nil? user-id)))

                    (is (not (nil? user-record)))

                    (is (= name-value (:name user-record)))

                    ;; FIXME: Fix code to put in alternate method from 'minimum'.
                    (is (= minimum/registration-method (:method user-record))))

                  (-> session-anon
                      (request (str validate-url "?code=GOOD")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*an account with the same email already exists.*")
                      (ltu/is-status 303))
                  )))))))))
