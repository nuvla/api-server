(ns com.sixsq.slipstream.ssclj.resources.user-template-github-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [com.sixsq.slipstream.auth.external :as ex]
    [com.sixsq.slipstream.auth.github :as auth-github]
    [com.sixsq.slipstream.auth.utils.db :as db]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.callback.utils :as cbu]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration :as configuration]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.user :as user]
    [com.sixsq.slipstream.ssclj.resources.user-template :as ut]
    [com.sixsq.slipstream.ssclj.resources.user-template-github :as github]
    [com.sixsq.slipstream.ssclj.resources.user.user-identifier-utils :as uiu]
    [com.sixsq.slipstream.ssclj.util.metadata-test-utils :as mdtu]
    [peridot.core :refer :all]
    [ring.util.codec :as codec]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase user/resource-name)))

(def configuration-base-uri (str p/service-context (u/de-camelcase configuration/resource-name)))

(def user-template-base-uri (str p/service-context (u/de-camelcase ut/resource-name)))

(def ^:const callback-pattern #".*/api/callback/.*/execute")

;; callback state reset between tests
(defn reset-callback! [callback-id]
  (cbu/update-callback-state! "WAITING" callback-id))


(def configuration-user-github {:configurationTemplate {:service      "session-github" ;;reusing configuration from session GitHub
                                                        :instance     github/registration-method
                                                        :clientID     "FAKE_CLIENT_ID"
                                                        :clientSecret "ABCDEF..."}})


(deftest check-metadata
  (mdtu/check-metadata-exists (str ut/resource-url "-" github/resource-url)))


(deftest lifecycle

  (let [href (str ut/resource-url "/" github/registration-method)
        template-url (str p/service-context ut/resource-url "/" github/registration-method)

        session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json")
                         (header authn-info-header "unknown ANON"))
        session-admin (header session-anon authn-info-header "admin ADMIN USER ANON")
        session-user (header session-anon authn-info-header "user USER ANON")
        session-anon-form (-> session-anon
                              (content-type user/form-urlencoded))

        redirect-uri-example "https://example.com/webui"]

    ;; must create the github user template; this is not created automatically
    (let [user-template (->> {:group  "my-group"
                              :icon   "some-icon"
                              :order  10
                              :hidden false}
                             (merge github/resource)
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

    ;; get user template so that user resources can be tested
    (let [name-attr "name"
          description-attr "description"
          properties-attr {:a "one", :b "two"}

          href-create {:name         name-attr
                       :description  description-attr
                       :properties   properties-attr
                       :userTemplate {:href href}}

          href-create-redirect {:userTemplate {:href        href
                                               :redirectURI redirect-uri-example}}
          invalid-create (assoc-in href-create [:userTemplate :invalid] "BAD")]

      ;; queries by anyone should succeed but have no entries
      (doseq [session [session-anon session-user session-admin]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)))

      ;; configuration must have GitHub client ID or secret, if not should get 500
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str href-create))
          (ltu/body->edn)
          (ltu/message-matches #".*missing or incorrect configuration.*")
          (ltu/is-status 500))

      ;; create the session-github configuration to use for these tests
      (let [cfg-href (-> session-admin
                         (request configuration-base-uri
                                  :request-method :post
                                  :body (json/write-str configuration-user-github))
                         (ltu/body->edn)
                         (ltu/is-status 201)
                         (ltu/location))]

        (is (= cfg-href (str "configuration/session-github-" github/registration-method)))

        (let [resp (-> session-anon
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
              uri (-> resp ltu/location)

              resp (-> session-anon
                       (request base-uri
                                :request-method :post
                                :body (json/write-str href-create-redirect))
                       (ltu/body->edn)
                       (ltu/is-status 303))
              uri2 (-> resp ltu/location)

              resp (-> session-anon-form
                       (request base-uri
                                :request-method :post
                                :body (codec/form-encode {:href        href
                                                          :redirectURI redirect-uri-example}))
                       (ltu/body->edn)
                       (ltu/is-status 303))
              uri3 (-> resp ltu/location)

              uris [uri uri2 uri3]]

          ;; redirect URLs in location header should contain the client ID and resource id
          (doseq [u uris]
            (is (re-matches #".*FAKE_CLIENT_ID.*" (or u "")))
            (is (re-matches callback-pattern (or u ""))))

          ;; anonymous, user and admin query should succeed but have no users
          (doseq [session [session-anon session-user session-admin]]
            (-> session
                (request base-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count zero?)))

          ;;
          ;; test validation callback
          ;;
          (let [get-redirect-uri #(->> % (re-matches #".*redirect_uri=(.*)$") second)
                get-callback-id #(->> % (re-matches #".*(callback.*)/execute$") second)

                validate-urls (map get-redirect-uri uris)
                callbacks (map get-callback-id validate-urls)]


            ;; all callbacks must exist
            (doseq [callback callbacks]
              (-> session-admin
                  (request (str p/service-context callback))
                  (ltu/body->edn)
                  (ltu/is-status 200)))


            ;; remove the authentication configuration
            (-> session-admin
                (request (str p/service-context cfg-href)
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; try hitting the callback with an invalid server configuration
            ;; when a redirectURI is present, the return is 303 even on errors
            (doseq [[url status] (map vector validate-urls [500 303 303])]
              (-> session-anon
                  (request url
                           :request-method :get)
                  (ltu/body->edn)
                  (ltu/message-matches #".*missing or incorrect configuration.*")
                  (ltu/is-status status)))

            ;; add the configuration back again
            (-> session-admin
                (request configuration-base-uri
                         :request-method :post
                         :body (json/write-str configuration-user-github))
                (ltu/body->edn)
                (ltu/is-status 201))

            ;; try hitting the callback without the OAuth code parameter
            ;; when a redirectURI is present, the return is 303 even on errors
            (doseq [[callback url status] (map vector callbacks validate-urls [400 303 303])]
              (reset-callback! callback)
              (-> session-anon
                  (request url
                           :request-method :get)
                  (ltu/body->edn)
                  (ltu/message-matches #".*not contain required code.*")
                  (ltu/is-status status)))

            ;; try hitting the callback with mocked codes
            (doseq [[callback url status create-status n] (map vector callbacks validate-urls [400 303 303] [201 303 303] (range))]
              (reset-callback! callback)

              (let [github-login (str "GITHUB_USER_" n)
                    email (format "user-%s@example.com" n)]

                (with-redefs [auth-github/get-github-access-token (fn [client-id client-secret oauth-code]
                                                                    (case oauth-code
                                                                      "GOOD" "GOOD_ACCESS_CODE"
                                                                      "BAD" "BAD_ACCESS_CODE"
                                                                      nil))
                              auth-github/get-github-user-info (fn [access-code]
                                                                 (when (= access-code "GOOD_ACCESS_CODE")
                                                                   {:login github-login, :email email}))

                              ex/match-existing-external-user (fn [authn-method external-login instance]
                                                                "MATCHED_USER")]

                  (-> session-anon
                      (request (str url "?code=NONE")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*unable to retrieve GitHub access code.*")
                      (ltu/is-status status))

                  (is (false? (db/user-exists? github-login)))

                  (reset-callback! callback)
                  (-> session-anon
                      (request (str url "?code=BAD")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*unable to retrieve GitHub user information.*")
                      (ltu/is-status status))

                  (is (nil? (uiu/find-username-by-identifier :github nil github-login)))

                  (reset-callback! callback)
                  (-> session-anon
                      (request (str url "?code=GOOD")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/is-status create-status))

                  (let [ss-username (uiu/find-username-by-identifier :github nil github-login)
                        user-record (->> github-login
                                         (uiu/find-username-by-identifier :github nil)
                                         (db/get-user))]
                    (is (not (nil? ss-username)))

                    (is (= email (:name user-record)))
                    (is (= github/registration-method (:method user-record))))

                  ;; try creating the same user again, should fail
                  (reset-callback! callback)
                  (-> session-anon
                      (request (str url "?code=GOOD")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*account already exists.*")
                      (ltu/is-status status))))))


          ;; create with invalid template fails
          (-> session-anon
              (request base-uri
                       :request-method :post
                       :body (json/write-str invalid-create))
              (ltu/body->edn)
              (ltu/is-status 400)))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id user/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]])))


