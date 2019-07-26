(ns sixsq.nuvla.server.resources.user-template-github-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.github :as auth-github]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.callback.utils :as cbu]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as ut]
    [sixsq.nuvla.server.resources.user-template-github :as github]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(def configuration-base-uri (str p/service-context configuration/resource-type))


(def user-template-base-uri (str p/service-context ut/resource-type))


(def ^:const callback-pattern #".*/api/callback/.*/execute")


;; callback state reset between tests
(defn reset-callback! [callback-id]
  (cbu/update-callback-state! "WAITING" callback-id))


(def configuration-user-github {:template {:service       "session-github" ;;reusing configuration from session GitHub
                                           :instance      github/registration-method
                                           :client-id     "FAKE_CLIENT_ID"
                                           :client-secret "ABCDEF..."}})


(deftest check-metadata
  (mdtu/check-metadata-exists (str ut/resource-type "-" github/resource-url)
                              (str ut/resource-type "-" github/resource-url "-create")))


(deftest lifecycle

  (let [href                 (str ut/resource-type "/" github/registration-method)
        template-url         (str p/service-context ut/resource-type "/" github/registration-method)

        session-anon         (-> (ltu/ring-app)
                                 session
                                 (content-type "application/json")
                                 (header authn-info/authn-info-header "unknown group/nuvla-anon"))
        session-admin        (header session-anon authn-info/authn-info-header "super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user         (header session-anon authn-info/authn-info-header "user group/nuvla-user group/nuvla-anon")

        redirect-url-example "https://example.com/webui"]

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
    (let [name-attr            "name"
          description-attr     "description"
          tags-attr            ["a-1" "b-2"]

          href-create          {:name        name-attr
                                :description description-attr
                                :tags        tags-attr
                                :template    {:href href}}

          href-create-redirect {:template {:href         href
                                           :redirect-url redirect-url-example}}
          invalid-create       (assoc-in href-create [:template :invalid] "BAD")]

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

        (let [resp         (-> session-anon
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str href-create))
                               (ltu/body->edn)
                               (ltu/is-status 303))

              uri          (-> resp ltu/location)

              resp         (-> session-anon
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str href-create-redirect))
                               (ltu/body->edn)
                               (ltu/is-status 303))
              uri2         (-> resp ltu/location)


              uris         [uri uri2]]

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

          (let [get-redirect-url #(->> % (re-matches #".*redirect_uri=(.*)$") second)
                get-callback-id  #(->> % (re-matches #".*(callback.*)/execute$") second)

                validate-urls    (map get-redirect-url uris)
                callbacks        (map get-callback-id validate-urls)]


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
            ;; when a redirect-url is present, the return is 303 even on errors
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
            ;; when a redirect-url is present, the return is 303 even on errors
            (doseq [[callback url status] (map vector callbacks validate-urls [400 303 303])]
              (reset-callback! callback)
              (-> session-anon
                  (request url)
                  (ltu/body->edn)
                  (ltu/message-matches #".*not contain required code.*")
                  (ltu/is-status status)))

            ;; try hitting the callback with mocked codes
            (doseq [[callback url status create-status n] (map vector callbacks validate-urls [400 303 303] [201 303 303] (range))]
              (reset-callback! callback)

              (let [github-login (str "GITHUB_USER_" n)
                    email        (format "user-%s@example.com" n)]

                (with-redefs [auth-github/get-github-access-token (fn [client-id client-secret oauth-code]
                                                                    (case oauth-code
                                                                      "GOOD" "GOOD_ACCESS_CODE"
                                                                      "BAD" "BAD_ACCESS_CODE"
                                                                      nil))
                              auth-github/get-github-user-info    (fn [access-code]
                                                                    (when (= access-code "GOOD_ACCESS_CODE")
                                                                      {:login github-login, :email email}))]

                  (-> session-anon
                      (request (str url "?code=NONE")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*unable to retrieve GitHub access code.*")
                      (ltu/is-status status))

                  (is (nil? (uiu/user-identifier->user-id :github "github" github-login)))

                  (reset-callback! callback)
                  (-> session-anon
                      (request (str url "?code=BAD")
                               :request-method :get)
                      (ltu/body->edn)
                      (ltu/message-matches #".*unable to retrieve GitHub user information.*")
                      (ltu/is-status status))

                  (is (nil? (uiu/user-identifier->user-id :github nil github-login)))

                  (reset-callback! callback)
                  (-> session-anon
                      (request (str url "?code=GOOD"))
                      (ltu/body->edn)
                      (ltu/is-status create-status))

                  (let [user-id     (uiu/user-identifier->user-id :github "github" github-login)
                        name-value  (uiu/generate-identifier :github "github" github-login)
                        user-record (ex/get-user user-id)]

                    (is (not (nil? user-id)))

                    (is (not (nil? user-record)))

                    (is (= name-value (:name user-record)))

                    ;; FIXME: Fix code to put in alternate method from 'minimum'.
                    (is (= minimum/registration-method (:method user-record))))

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
