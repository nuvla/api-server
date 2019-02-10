(ns sixsq.nuvla.server.resources.session-internal-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.auth.internal :as auth-internal]
    [sixsq.nuvla.auth.utils.db :as db]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as ct]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-internal :as internal]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context session/resource-type))


(def session-template-base-uri (str p/service-context ct/resource-type))


(def session-template-internal {:method      internal/authn-method
                                :instance    internal/authn-method
                                :name        "Internal"
                                :description "Internal Authentication via Username/Password"
                                :username    "username"
                                :password    "password"
                                :acl         st/resource-acl})


(defn mock-login-valid?
  "Will return true if the username and password are identical;
   false otherwise.  Avoids having to start a real database and
   populate it with users."
  [{:keys [username password]}]
  (= username password))


(defn mock-roles
  "Mocking function to return the roles for a given user.  For
   'root' the 'ADMIN', 'USER', and 'ANON' roles will be added. F
   For all others, the 'USER' and 'ANON' roles will be added."
  [username]
  (str/join " " (case username
                  "root" ["ADMIN" "USER" "ANON"]
                  ["USER" "ANON"])))


(deftest lifecycle

  (with-redefs [auth-internal/valid? mock-login-valid?
                db/find-roles-for-username mock-roles]

    ;; check that the mocking is working correctly
    (is (auth-internal/valid? {:username "user" :password "user"}))
    (is (not (auth-internal/valid? {:username "user" :password "BAD"})))
    (is (= (str/join " " ["ADMIN" "USER" "ANON"]) (db/find-roles-for-username "root")))
    (is (= (str/join " " ["USER" "ANON"]) (db/find-roles-for-username "user")))

    ;; get session template so that session resources can be tested
    (let [app (ltu/ring-app)
          session-json (content-type (session app) "application/json")
          session-anon (header session-json authn-info-header "unknown ANON")
          session-user (header session-json authn-info-header "user USER")
          session-admin (header session-json authn-info-header "root ADMIN")

          ;;
          ;; session template should already exist after test server initialization
          ;;
          href (str st/resource-type "/internal")

          template-url (str p/service-context href)

          resp (-> session-anon
                   (request template-url)
                   (ltu/body->edn)
                   (ltu/is-status 200))
          template (get-in resp [:response :body])

          name-attr "name"
          description-attr "description"
          tags-attr ["one", "two"]

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href     href
                                      :username "user"
                                      :password "user"}}
          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")
          unauthorized-create (update-in valid-create [:template :password] (constantly "BAD"))
          invalid-create (assoc-in valid-create [:template :invalid] "BAD")
          invalid-create-redirect (assoc-in valid-create-redirect [:template :invalid] "BAD")]

      ;; anonymous query should succeed but have no entries
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      ;; unauthorized create must return a 403 response
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str unauthorized-create))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; anonymous create must succeed; also with redirect
      (let [resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str valid-create))
                     (ltu/body->edn)
                     (ltu/is-set-cookie)
                     (ltu/is-status 201))
            id (get-in resp [:response :body :resource-id])

            token (get-in resp [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
            claims (if token (sign/unsign-claims token) {})

            uri (-> resp
                    (ltu/location))
            abs-uri (str p/service-context uri)

            resp2 (-> session-anon
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-create-redirect))
                      (ltu/body->edn)
                      (ltu/is-set-cookie)
                      (ltu/is-status 303))
            id2 (get-in resp2 [:response :body :resource-id])

            token2 (get-in resp2 [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
            claims2 (if token2 (sign/unsign-claims token2) {})

            uri2 (-> resp2
                     (ltu/location))
            abs-uri2 (str p/service-context uri2)]

        ;; check claims in cookie
        (is (= "user" (:username claims)))
        (is (= (str/join " " ["USER" "ANON" uri]) (:roles claims))) ;; uri is also session id
        (is (= uri (:session claims)))                      ;; uri is also session id
        (is (not (nil? (:exp claims))))

        ;; check claims in cookie for redirect
        (is (= "user" (:username claims2)))
        (is (= (str/join " " ["USER" "ANON" id2]) (:roles claims2))) ;; uri is also session id
        (is (= id2 (:session claims2)))                     ;; uri is also session id
        (is (not (nil? (:exp claims2))))
        (is (= "http://redirect.example.org" uri2))

        ;; user should not be able to see session without session role
        (-> session-user
            (request abs-uri)
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
        (let [{:keys [name description tags] :as body} (-> session-user
                                                           (header authn-info-header (str "user USER ANON " id))
                                                           (request abs-uri)
                                                           (ltu/body->edn)
                                                           :response
                                                           :body)]
          (is (= name name-attr))
          (is (= description description-attr))
          (is (= tags tags-attr)))

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
            (ltu/is-status 400)))

      ;; admin create must also succeed
      (let [create-req (-> valid-create
                           (assoc-in [:template :username] "root")
                           (assoc-in [:template :password] "root"))
            resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str create-req))
                     (ltu/is-set-cookie)
                     (ltu/body->edn)
                     (ltu/is-status 201))
            id (get-in resp [:response :body :resource-id])
            uri (-> resp
                    (ltu/location))
            abs-uri (str p/service-context uri)]

        ;; admin should be able to see and delete session with session role
        (-> (session app)
            (header authn-info-header (str "root ADMIN " id))
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))

        ;; admin can delete resource with session role
        (-> (session app)
            (header authn-info-header (str "root ADMIN " id))
            (request abs-uri
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200)))

      ;; admin create with invalid template fails
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str invalid-create))
          (ltu/body->edn)
          (ltu/is-status 400))

      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str invalid-create-redirect))
          (ltu/body->edn)
          (ltu/is-status 303)))))

