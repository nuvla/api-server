(ns sixsq.nuvla.server.resources.session-github-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.github :as auth-github]
    [sixsq.nuvla.auth.utils.db :as db]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.callback.utils :as cbu]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as ct]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-github :as github]
    [peridot.core :refer :all]
    [ring.util.codec :as codec]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase session/resource-name)))

(def configuration-base-uri (str p/service-context (u/de-camelcase configuration/resource-name)))

(def session-template-base-uri (str p/service-context (u/de-camelcase ct/resource-name)))

(def ^:const callback-pattern #".*/api/callback/.*/execute")



(def instance "test-github")
(def session-template-github {:method      github/authn-method
                              :instance    instance
                              :name        "GitHub"
                              :description "External Authentication with GitHub Credentials"
                              :acl         st/resource-acl})

(def configuration-session-github {:configurationTemplate {:service      "session-github"
                                                           :instance     instance
                                                           :clientID     "FAKE_CLIENT_ID"
                                                           :clientSecret "ABCDEF..."}})

;; callback state reset between tests
(defn reset-callback! [callback-id]
  (cbu/update-callback-state! "WAITING" callback-id))

(deftest lifecycle

  (let [app (ltu/ring-app)
        session (-> (session app)
                    (content-type "application/json"))
        session-admin (header session authn-info-header "admin ADMIN USER ANON")
        session-user (header session authn-info-header "user USER ANON")
        session-anon (header session authn-info-header "unknown ANON")
        session-anon-form (-> session-anon
                              (content-type u/form-urlencoded))

        redirect-uri-example "https://example.com/webui"]

    ;; get session template so that session resources can be tested
    (let [
          ;;
          ;; create the session template to use for these tests
          ;;
          href (-> session-admin
                   (request session-template-base-uri
                            :request-method :post
                            :body (json/write-str session-template-github))
                   (ltu/body->edn)
                   (ltu/is-status 201)
                   (ltu/location))

          template-url (str p/service-context href)

          resp (-> session-anon
                   (request template-url)
                   (ltu/body->edn)
                   (ltu/is-status 200))
          template (get-in resp [:response :body])

          name-attr "name"
          description-attr "description"
          properties-attr {:a "one", :b "two"}

          href-create {:name            name-attr
                       :description     description-attr
                       :properties      properties-attr
                       :sessionTemplate {:href href}}
          href-create-redirect {:sessionTemplate {:href        href
                                                  :redirectURI redirect-uri-example}}
          invalid-create (assoc-in href-create [:sessionTemplate :invalid] "BAD")
          invalid-create-redirect (assoc-in href-create-redirect [:sessionTemplate :invalid] "BAD")]

      ;; anonymous query should succeed but have no entries
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      ;; configuration must have GitHub client ID or secret, if not should get 500
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str href-create))
          (ltu/body->edn)
          (ltu/message-matches #".*missing or incorrect configuration.*")
          (ltu/is-status 500))

      ;; anonymous create must succeed (normal create and href create)
      (let [
            ;;
            ;; create the session-github configuration to use for these tests
            ;;
            cfg-href (-> session-admin
                         (request configuration-base-uri
                                  :request-method :post
                                  :body (json/write-str configuration-session-github))
                         (ltu/body->edn)
                         (ltu/is-status 201)
                         (ltu/location))

            _ (is (= cfg-href (str "configuration/session-github-" instance)))

            resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str href-create))
                     (ltu/body->edn)
                     (ltu/is-status 303))

            redirect-uri (-> session-anon
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str href-create))
                             (ltu/body->edn)
                             (ltu/location))

            id (get-in resp [:response :body :resource-id])
            uri (-> resp
                    (ltu/location))
            abs-uri (str p/service-context id)

            resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str href-create-redirect))
                     (ltu/body->edn)
                     (ltu/is-status 303))
            id2 (get-in resp [:response :body :resource-id])
            uri2 (-> resp
                     (ltu/location))
            abs-uri2 (str p/service-context id2)

            resp (-> session-anon-form
                     (request base-uri
                              :request-method :post
                              :body (codec/form-encode {:href        href
                                                        :redirectURI redirect-uri}))
                     (ltu/body->edn)
                     (ltu/is-status 303))
            id3 (get-in resp [:response :body :resource-id])
            uri3 (-> resp
                     (ltu/location))
            abs-uri3 (str p/service-context id3)]


        ;; redirect URLs in location header should contain a link to the callback execute url
        (is (re-matches #".*FAKE_CLIENT_ID.*" (or uri "")))
        (is (re-matches callback-pattern (or uri "")))
        (is (re-matches #".*FAKE_CLIENT_ID.*" (or uri2 "")))
        (is (re-matches callback-pattern (or uri2 "")))
        (is (re-matches #".*FAKE_CLIENT_ID.*" (or uri3 "")))
        (is (re-matches callback-pattern (or uri3 "")))

        ;; user should not be able to see session without session role
        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403))
        (-> session-user
            (request abs-uri2)
            (ltu/body->edn)
            (ltu/is-status 403))
        (-> session-user
            (request abs-uri3)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; anonymous query should succeed but still have no entries
        (-> session-anon
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        ;; user query should succeed but have no entries because of missing session role
        (-> session-user
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        ;; admin query should succeed, but see no sessions without the correct session role
        (-> session-admin
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 0))

        ;; user should be able to see session with session role
        (-> session-user
            (header authn-info-header (str "user USER ANON " id))
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))
        (-> session-user
            (header authn-info-header (str "user USER ANON " id2))
            (request abs-uri2)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id2)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))
        (-> session-user
            (header authn-info-header (str "user USER ANON " id3))
            (request abs-uri3)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id3)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))

        ;; check contents of session
        (let [{:keys [name description properties] :as body} (-> session-user
                                                                 (header authn-info-header (str "user USER ANON " id))
                                                                 (request abs-uri)
                                                                 (ltu/body->edn)
                                                                 :response
                                                                 :body)]
          (is (= name name-attr))
          (is (= description description-attr))
          (is (= properties properties-attr)))

        ;; user query with session role should succeed but and have one entry
        (-> session-user
            (header authn-info-header (str "user USER ANON " id))
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))
        (-> session-user
            (header authn-info-header (str "user USER ANON " id2))
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))
        (-> session-user
            (header authn-info-header (str "user USER ANON " id3))
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))

        ;;
        ;; test validation callback
        ;;
        (let [get-redirect-uri (fn [u]
                                 (let [r #".*redirect_uri=(.*)$"]
                                   (second (re-matches r u))))
              get-callback-id (fn [u]
                                (let [r #".*(callback.*)/execute$"]
                                  (second (re-matches r u))))
              validate-url (get-redirect-uri uri)
              validate-url2 (get-redirect-uri uri2)
              validate-url3 (get-redirect-uri uri3)
              callback-id (get-callback-id validate-url)
              callback-id2 (get-callback-id validate-url2)
              callback-id3 (get-callback-id validate-url3)]

          (-> session-admin
              (request (str p/service-context callback-id))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-admin
              (request (str p/service-context callback-id2))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-admin
              (request (str p/service-context callback-id3))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; remove the authentication configuration
          (-> session-admin
              (request (str p/service-context cfg-href)
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; try hitting the callback with an invalid server configuration

          (-> session-anon
              (request validate-url
                       :request-method :get)
              (ltu/body->edn)
              (ltu/message-matches #".*missing or incorrect configuration.*")
              (ltu/is-status 500))

          (-> session-anon
              (request validate-url2
                       :request-method :get)
              (ltu/body->edn)
              (ltu/message-matches #".*missing or incorrect configuration.*")
              (ltu/is-status 303))                          ;; always expect redirect when redirectURI is provided

          (-> session-anon
              (request validate-url3
                       :request-method :get)
              (ltu/body->edn)
              (ltu/message-matches #".*missing or incorrect configuration.*")
              (ltu/is-status 303))                          ;; always expect redirect when redirectURI is provided

          ;; add the configuration back again
          (-> session-admin
              (request configuration-base-uri
                       :request-method :post
                       :body (json/write-str configuration-session-github))
              (ltu/body->edn)
              (ltu/is-status 201))

          ;; reset callback states, otherwise we'll get error 409
          (reset-callback! callback-id)
          (reset-callback! callback-id2)
          (reset-callback! callback-id3)

          ;; try hitting the callback without the OAuth code parameter
          (-> session-anon
              (request validate-url
                       :request-method :get)
              (ltu/body->edn)
              (ltu/message-matches #".*not contain required code.*")
              (ltu/is-status 400))

          (-> session-anon
              (request validate-url2
                       :request-method :get)
              (ltu/body->edn)
              (ltu/message-matches #".*not contain required code.*")
              (ltu/is-status 303))

          (-> session-anon
              (request validate-url3
                       :request-method :get)
              (ltu/body->edn)
              (ltu/message-matches #".*not contain required code.*")
              (ltu/is-status 303))


          ;; try now with a fake code
          (reset-callback! callback-id)

          (with-redefs [auth-github/get-github-access-token (fn [client-id client-secret oauth-code]
                                                              (case oauth-code
                                                                "GOOD" "GOOD_ACCESS_CODE"
                                                                "BAD" "BAD_ACCESS_CODE"
                                                                nil))
                        auth-github/get-github-user-info (fn [access-code]
                                                           (when (= access-code "GOOD_ACCESS_CODE")
                                                             {:login "GITHUB_USER"
                                                              :email "user@example.com"}))

                        ex/match-existing-external-user (fn [authn-method external-login instance]
                                                          "MATCHED_USER")

                        db/find-roles-for-username (fn [username]
                                                     "USER ANON alpha")]

            (-> session-anon
                (request (str validate-url "?code=NONE")
                         :request-method :get)
                (ltu/body->edn)
                (ltu/message-matches #".*unable to retrieve GitHub access code.*")
                (ltu/is-status 400))

            (reset-callback! callback-id)
            (-> session-anon
                (request (str validate-url "?code=BAD")
                         :request-method :get)
                (ltu/body->edn)
                (ltu/message-matches #".*unable to retrieve GitHub user information.*")
                (ltu/is-status 400))


            (let [_ (reset-callback! callback-id)
                  ring-info (-> session-anon
                                (request (str validate-url "?code=GOOD")
                                         :request-method :get)
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/is-set-cookie))
                  location (ltu/location ring-info)
                  token (get-in ring-info [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
                  claims (if token (sign/unsign-claims token) {})]
              (is (= location id))
              (is (= "MATCHED_USER" (:username claims)))
              (is (re-matches (re-pattern (str ".*" id ".*")) (or (:roles claims) ""))))

            (let [_ (reset-callback! callback-id2)
                  ring-info (-> session-anon
                                (request (str validate-url2 "?code=GOOD")
                                         :request-method :get)
                                (ltu/body->edn)
                                (ltu/is-status 303)
                                (ltu/is-set-cookie))
                  location (ltu/location ring-info)
                  token (get-in ring-info [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
                  claims (if token (sign/unsign-claims token) {})]
              (is (= location redirect-uri-example))
              (is (= "MATCHED_USER" (:username claims)))
              (is (re-matches (re-pattern (str ".*" id2 ".*")) (or (:roles claims) ""))))

            (let [_ (reset-callback! callback-id3)
                  ring-info (-> session-anon
                                (request (str validate-url3 "?code=GOOD")
                                         :request-method :get)
                                (ltu/body->edn)
                                (ltu/is-status 303)
                                (ltu/is-set-cookie))
                  location (ltu/location ring-info)
                  token (get-in ring-info [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
                  claims (if token (sign/unsign-claims token) {})]
              (is (= location redirect-uri))
              (is (= "MATCHED_USER" (:username claims)))
              (is (re-matches (re-pattern (str ".*" id3 ".*")) (or (:roles claims) ""))))))

        ;; check that the session has been updated
        (let [ring-info (-> session-user
                            (header authn-info-header (str "user USER ANON " id))
                            (request abs-uri)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-id id)
                            (ltu/is-operation-present "delete")
                            (ltu/is-operation-absent (:validate c/action-uri))
                            (ltu/is-operation-absent "edit"))
              session (get-in ring-info [:response :body])]
          (is (= "MATCHED_USER" (:username session)))
          (is (not= (:created session) (:updated session))))

        (let [ring-info (-> session-user
                            (header authn-info-header (str "user USER ANON " id2))
                            (request abs-uri2)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-id id2)
                            (ltu/is-operation-present "delete")
                            (ltu/is-operation-absent (:validate c/action-uri))
                            (ltu/is-operation-absent "edit"))
              session (get-in ring-info [:response :body])]
          (is (= "MATCHED_USER" (:username session)))
          (is (not= (:created session) (:updated session))))

        (let [ring-info (-> session-user
                            (header authn-info-header (str "user USER ANON " id3))
                            (request abs-uri3)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-id id3)
                            (ltu/is-operation-present "delete")
                            (ltu/is-operation-absent (:validate c/action-uri))
                            (ltu/is-operation-absent "edit"))
              session (get-in ring-info [:response :body])]
          (is (= "MATCHED_USER" (:username session)))
          (is (not= (:created session) (:updated session))))

        ;; user with session role can delete resource
        (-> session-user
            (header authn-info-header (str "user USER ANON " id))
            (request abs-uri
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200))
        (-> session-user
            (header authn-info-header (str "user USER ANON " id2))
            (request abs-uri2
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200))
        (-> session-user
            (header authn-info-header (str "user USER ANON " id3))
            (request abs-uri3
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; create with invalid template fails
        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-create))
            (ltu/body->edn)
            (ltu/is-status 400))

        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-create-redirect))
            (ltu/body->edn)
            (ltu/is-status 303))))))
