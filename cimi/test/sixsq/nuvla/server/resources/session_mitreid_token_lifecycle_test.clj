(ns sixsq.nuvla.server.resources.session-mitreid-token-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.auth.external :as ex]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as ct]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-mitreid-token :as mitreid-token]
    [environ.core :as env]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context (u/de-camelcase session/resource-name)))

(def session-template-base-uri (str p/service-context (u/de-camelcase ct/resource-name)))

(def configuration-base-uri (str p/service-context (u/de-camelcase configuration/resource-name)))

(def instance "test-instance")


(def access-token (sign/sign-claims {:sub "user-id-number"}))


(def session-template-mitreid {:method      mitreid-token/authn-method
                               :instance    instance
                               :name        "MITREid Token Authentication"
                               :description "External Authentication with MITREid Token"
                               :token       access-token
                               :acl         st/resource-acl})


(defn marker?
  [line]
  (boolean (re-matches #"^---.*$" line)))


(def auth-pubkey (with-open [rdr (io/reader (env/env :auth-public-key))]
                   (->> rdr line-seq (remove marker?) (str/join ""))))


(def valid-ip "192.168.100.100")


(def configuration-session-mitreid
  {:configurationTemplate {:service        "session-mitreid"
                           :instance       instance
                           :clientID       "FAKE_CLIENT_ID"
                           :clientSecret   "MyMITREidClientSecret"
                           :authorizeURL   "https://authorize.mitreid.com/authorize"
                           :tokenURL       "https://token.mitreid.com/token"
                           :userProfileURL "https://userinfo.mitreid.com/api/user/me"
                           :publicKey      auth-pubkey}})


(def configuration-session-mitreid-token
  {:configurationTemplate {:service   "session-mitreid-token"
                           :instance  instance
                           :clientIPs [valid-ip]}})


(deftest lifecycle

  (with-redefs [ex/match-oidc-username (constantly "slipstream-username")]

    (let [app (ltu/ring-app)
          session-json (content-type (session app) "application/json")
          session-anon (header session-json authn-info-header "unknown ANON")
          session-user (header session-json authn-info-header "user USER")
          session-admin (header session-json authn-info-header "root ADMIN")]

      ;;
      ;; create the session template and the configuration for the tests
      ;;

      (let [href (-> session-admin
                     (request session-template-base-uri
                              :request-method :post
                              :body (json/write-str session-template-mitreid))
                     (ltu/body->edn)
                     (ltu/is-status 201)
                     (ltu/location))

            template-url (str p/service-context href)

            cfg-href (-> session-admin
                         (request configuration-base-uri
                                  :request-method :post
                                  :body (json/write-str configuration-session-mitreid))
                         (ltu/body->edn)
                         (ltu/is-status 201)
                         (ltu/location))

            cfg-url (str p/service-context cfg-href)

            token-cfg-url (-> session-admin
                              (request configuration-base-uri
                                       :request-method :post
                                       :body (json/write-str configuration-session-mitreid-token))
                              (ltu/body->edn)
                              (ltu/is-status 201)
                              (ltu/location))

            token-cfg-url (str p/service-context token-cfg-url)]

        ;; verify that the session template exists
        (-> session-anon
            (request template-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the MITREid configuration exists
        (-> session-admin
            (request cfg-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the MITREid token configuration exists
        (-> session-admin
            (request token-cfg-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; anonymous query should succeed but have no entries
        (-> session-anon
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        (let [name-attr "name"
              description-attr "description"
              properties-attr {:a "one", :b "two"}

              valid-create {:name            name-attr
                            :description     description-attr
                            :properties      properties-attr
                            :sessionTemplate {:href  href
                                              :token access-token}}

              valid-create-redirect (assoc-in valid-create [:sessionTemplate :redirectURI] "http://redirect.example.org")
              invalid-create (assoc-in valid-create [:sessionTemplate :invalid] "BAD")]

          ;; invalid create should return a 400
          (-> session-anon
              (request base-uri
                       :request-method :post
                       :body (json/write-str invalid-create))
              (ltu/body->edn)
              (ltu/is-status 400))

          ;; standard anonymous create fails because of wrong IP address
          (-> session-anon
              (header "x-real-ip" "127.0.0.1")              ;; not an allowed IP address
              (request base-uri
                       :request-method :post
                       :body (json/write-str valid-create))
              (ltu/body->edn)
              (ltu/is-status 400))

          (let [
                ;; standard anonymous create works with 201 response
                resp (-> session-anon
                         (header "x-real-ip" valid-ip)
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str valid-create))
                         (ltu/body->edn)
                         (ltu/is-set-cookie)
                         (ltu/is-status 201))

                id (get-in resp [:response :body :resource-id])
                token (get-in resp [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
                claims (if token (sign/unsign-claims token) {})
                uri (-> resp ltu/location)
                abs-uri (str p/service-context uri)

                ;; with redirect should always return a 303
                resp (-> session-anon
                         (header "x-real-ip" valid-ip)
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str valid-create-redirect))
                         (ltu/body->edn)
                         (ltu/is-set-cookie)
                         (ltu/is-status 303))

                id2 (get-in resp [:response :body :resource-id])
                token2 (get-in resp [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
                claims2 (if token2 (sign/unsign-claims token2) {})
                uri2 (-> resp ltu/location)                 ;; redirect, not session ID!
                abs-uri2 (str p/service-context id2)]

            ;; check claims in cookie
            (is (= "slipstream-username" (:username claims)))
            (is (= #{"USER" "ANON" id} (some-> claims :roles (str/split #"\s+") set)))
            (is (= uri (:session claims)))
            (is (not (nil? (:exp claims))))

            ;; check claims in cookie for redirect
            (is (= "slipstream-username" (:username claims2)))
            (is (= #{"USER" "ANON" id2} (some-> claims2 :roles (str/split #"\s+") set)))
            (is (= id2 (:session claims2)))
            (is (not (nil? (:exp claims2))))
            (is (= "http://redirect.example.org" uri2))

            ;; user should not be able to see session without session role
            (-> session-user
                (request abs-uri)
                (ltu/body->edn)
                (ltu/is-status 403))

            (-> session-user
                (request abs-uri2)
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
            (-> (session app)
                (header authn-info-header (str "user USER " id))
                (request abs-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-id id)
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
            (-> (session app)
                (header authn-info-header (str "user USER " id))
                (request base-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 1))

            ;; user with session role can delete resource
            (-> (session app)
                (header authn-info-header (str "user USER " id))
                (request abs-uri
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
                (ltu/is-status 400))))))))

