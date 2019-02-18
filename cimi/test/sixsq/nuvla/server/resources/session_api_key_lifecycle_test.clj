(ns sixsq.nuvla.server.resources.session-api-key-lifecycle-test
  (:require
    [clj-time.core :as time]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template-api-key :as api-key-tpl]
    [sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-api-key :as t]
    [sixsq.nuvla.server.resources.session-template :as ct]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session-template-api-key :as api-key]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context session/resource-type))

(def session-template-base-uri (str p/service-context ct/resource-type))


(def session-template-api-key {:method      api-key/authn-method
                               :instance    api-key/authn-method
                               :name        "API Key"
                               :description "Authentication with API Key and Secret"
                               :key         "key"
                               :secret      "secret"
                               :acl         st/resource-acl})

(deftest check-uuid->id
  (let [uuid (u/random-uuid)
        correct-id (str "credential/" uuid)]
    (is (= correct-id (t/uuid->id uuid)))
    (is (= correct-id (t/uuid->id correct-id)))))

(deftest check-valid-api-key
  (let [type api-key-tpl/credential-type
        expired (-> 10 time/seconds time/ago u/unparse-timestamp-datetime)
        current (-> 1 time/hours time/from-now u/unparse-timestamp-datetime)
        [secret digest] (key-utils/generate)
        [_ bad-digest] (key-utils/generate)
        valid-api-key {:type   type
                       :expiry current
                       :digest digest}]
    (is (true? (t/valid-api-key? valid-api-key secret)))
    (are [v] (true? (t/valid-api-key? v secret))
             valid-api-key
             (dissoc valid-api-key :expiry))
    (are [v] (false? (t/valid-api-key? v secret))
             {}
             (dissoc valid-api-key :type)
             (assoc valid-api-key :type "incorrect-type")
             (assoc valid-api-key :expiry expired)
             (assoc valid-api-key :digest bad-digest))
    (is (false? (t/valid-api-key? valid-api-key "bad-secret")))))

(deftest check-create-claims
  (let [username "root"
        server "nuv.la"
        headers {:slipstream-ssl-server-name server}
        roles ["ADMIN" "USER" "ANON"]
        session-id "session/72e9f3d8-805a-421b-b3df-86f1af294233"
        client-ip "127.0.0.1"]
    (is (= {:username username
            :session  session-id
            :roles    (str/join " " ["ADMIN" "USER" "ANON" session-id])
            :server   server
            :clientIP client-ip}
           (t/create-claims username roles headers session-id client-ip)))))

(defn mock-retrieve-by-id [doc-id]
  nil)

(deftest lifecycle

  (let [[secret digest] (key-utils/generate)
        [_ bad-digest] (key-utils/generate)
        uuid (u/random-uuid)
        valid-api-key {:id     (str "credential/" uuid)
                       :type   api-key-tpl/credential-type
                       :method api-key-tpl/method
                       :expiry (-> 1 time/hours time/from-now u/unparse-timestamp-datetime)
                       :digest digest
                       :claims {:identity "jane"
                                :roles    ["USER" "ANON"]}}
        mock-retrieve-by-id {(:id valid-api-key) valid-api-key
                             uuid                valid-api-key}]

    (with-redefs [t/retrieve-credential-by-id mock-retrieve-by-id]

      ;; check that the mocking is working correctly
      (is (= valid-api-key (t/retrieve-credential-by-id (:id valid-api-key))))
      (is (= valid-api-key (t/retrieve-credential-by-id uuid)))

      (let [app (ltu/ring-app)
            session-json (content-type (session app) "application/json")
            session-anon (header session-json authn-info-header "unknown ANON")
            session-user (header session-json authn-info-header "user USER ANON")
            session-admin (header session-json authn-info-header "root ADMIN USER ANON")

            ;;
            ;; create the session template to use for these tests
            ;;
            href (-> session-admin
                     (request session-template-base-uri
                              :request-method :post
                              :body (json/write-str session-template-api-key))
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
            tags-attr ["one", "two"]

            valid-create {:name        name-attr
                          :description description-attr
                          :tags        tags-attr
                          :template    {:href   href
                                        :key    uuid
                                        :secret secret}}
            valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")
            unauthorized-create (update-in valid-create [:template :secret] (constantly bad-digest))
            invalid-create (assoc-in valid-create [:template :invalid] "BAD")]

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
          (is (= "jane" (:username claims)))
          (is (= (str/join " " ["USER" "ANON" uri]) (:roles claims))) ;; uri is also session id
          (is (= uri (:session claims)))                    ;; uri is also session id
          (is (not (nil? (:exp claims))))

          ;; check claims in cookie for redirect
          (is (= "jane" (:username claims2)))
          (is (= (str/join " " ["USER" "ANON" id2]) (:roles claims2))) ;; uri is also session id
          (is (= id2 (:session claims2)))                   ;; uri is also session id
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

          ;; user query with session role should succeed but and have one entry
          (-> (session app)
              (header authn-info-header (str "user USER " id))
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 1))

          ;; check contents of session resource
          (let [{:keys [name description tags] :as body} (-> (session app)
                                                             (header authn-info-header (str "user USER " id))
                                                             (request abs-uri)
                                                             (ltu/body->edn)
                                                             :response
                                                             :body)]
            (is (= name name-attr))
            (is (= description description-attr))
            (is (= tags tags-attr)))

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
              (ltu/is-status 400)))))))
