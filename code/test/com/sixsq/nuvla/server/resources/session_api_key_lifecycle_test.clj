(ns com.sixsq.nuvla.server.resources.session-api-key-lifecycle-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [are deftest is use-fixtures]]
    [com.sixsq.nuvla.auth.cookies :as cookies]
    [com.sixsq.nuvla.auth.utils.sign :as sign]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-cookie authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-template-api-key :as api-key-tpl]
    [com.sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.session :as session]
    [com.sixsq.nuvla.server.resources.session-api-key :as t]
    [com.sixsq.nuvla.server.resources.session-template :as st]
    [com.sixsq.nuvla.server.resources.session-template-api-key :as api-key]
    [com.sixsq.nuvla.server.util.time :as time]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context session/resource-type))

(def session-template-base-uri (str p/service-context st/resource-type))


(def session-template-api-key {:method      api-key/authn-method
                               :instance    api-key/authn-method
                               :name        "API Key"
                               :description "Authentication with API Key and Secret"
                               :key         "key"
                               :secret      "secret"
                               :acl         st/resource-acl})

(deftest check-uuid->id
  (let [uuid       (u/rand-uuid)
        correct-id (str "credential/" uuid)]
    (is (= correct-id (t/uuid->id uuid)))
    (is (= correct-id (t/uuid->id correct-id)))))

(deftest check-valid-api-key
  (let [subtype       api-key-tpl/credential-subtype
        expired       (time/to-str (time/ago 10 :seconds))
        current       (time/to-str (time/from-now 1 :hours))
        [secret digest] (key-utils/generate)
        [_ bad-digest] (key-utils/generate)
        valid-api-key {:subtype subtype
                       :expiry  current
                       :digest  digest}]
    (is (true? (t/valid-api-key? valid-api-key secret)))
    (are [v] (true? (t/valid-api-key? v secret))
             valid-api-key
             (dissoc valid-api-key :expiry))
    (are [v] (false? (t/valid-api-key? v secret))
             {}
             (dissoc valid-api-key :subtype)
             (assoc valid-api-key :subtype "incorrect-subtype")
             (assoc valid-api-key :expiry expired)
             (assoc valid-api-key :digest bad-digest))
    (is (false? (t/valid-api-key? valid-api-key "bad-secret")))))

(deftest check-create-claims
  (let [user-id    "user/root"
        server     "nuvla.io"
        headers    {:nuvla-ssl-server-name server}
        claims     #{"user/root" "group/nuvla-user" "group/nuvla-anon"}
        session-id "session/72e9f3d8-805a-421b-b3df-86f1af294233"
        client-ip  "127.0.0.1"]
    (is (= {:client-ip "127.0.0.1"
            :claims    (str "group/nuvla-anon group/nuvla-user user/root " session-id)
            :user-id   "user/root"
            :server    "nuvla.io"
            :session   "session/72e9f3d8-805a-421b-b3df-86f1af294233"}
           (cookies/create-cookie-info user-id
                                       :claims claims
                                       :headers headers
                                       :session-id session-id
                                       :client-ip client-ip)))))


(deftest lifecycle

  (let [[secret digest] (key-utils/generate)
        [_ bad-digest] (key-utils/generate)
        uuid                (u/rand-uuid)
        credential-id       (str "credential/" uuid)
        user-id             "user/abcdef01-abcd-abcd-abcd-abcdef012345"
        valid-api-key       {:id      credential-id
                             :subtype api-key-tpl/credential-subtype
                             :method  api-key-tpl/method
                             :expiry  (time/to-str (time/from-now 1 :hours))
                             :digest  digest
                             :claims  {:identity user-id
                                       :roles    ["group/nuvla-user" "group/nuvla-anon"]}}
        mock-retrieve-by-id {(:id valid-api-key) valid-api-key
                             uuid                valid-api-key}]

    (with-redefs [t/retrieve-credential-by-id mock-retrieve-by-id]

      ;; check that the mocking is working correctly
      (is (= valid-api-key (t/retrieve-credential-by-id (:id valid-api-key))))
      (is (= valid-api-key (t/retrieve-credential-by-id uuid)))

      (let [app                 (ltu/ring-app)
            session-json        (content-type (session app) "application/json")
            session-anon        (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
            session-user        (header session-json authn-info-header "user/user group/nuvla-user group/nuvla-anon")
            session-admin       (header session-json authn-info-header
                                        "group/nuvla-admin group/nuvla-user group/nuvla-anon")

            ;;
            ;; create the session template to use for these tests
            ;;
            href                (str st/resource-type "/api-key")

            name-attr           "name"
            description-attr    "description"
            tags-attr           ["one", "two"]

            valid-create        {:name        name-attr
                                 :description description-attr
                                 :tags        tags-attr
                                 :template    {:href   href
                                               :key    uuid
                                               :secret secret}}
            unauthorized-create (update-in valid-create [:template :secret] (constantly bad-digest))
            invalid-create      (assoc-in valid-create [:template :invalid] "BAD")
            event-authn-info    {:user-id      "user/unknown"
                                 :active-claim "user/unknown"
                                 :claims       ["user/unknown" "group/nuvla-anon"]}]

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
                     :body (j/write-value-as-string unauthorized-create))
            (ltu/body->edn)
            (ltu/is-status 403))

        (ltu/is-last-event credential-id {:name               "session.add"
                                          :description        "Login attempt failed"
                                          :category           "add"
                                          :success            false
                                          :linked-identifiers [credential-id]
                                          :authn-info         event-authn-info
                                          :acl                {:owners ["group/nuvla-admin" user-id]}})

        ;; anonymous create must succeed; also with redirect
        (let [resp        (-> session-anon
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-create))
                              (ltu/body->edn)
                              (ltu/is-set-cookie)
                              (ltu/is-status 201))
              id          (ltu/body-resource-id resp)
              _           (ltu/is-last-event id {:name               "session.add"
                                                 :description        (str (:id valid-api-key) " logged in")
                                                 :category           "add"
                                                 :success            true
                                                 :linked-identifiers [credential-id]
                                                 :authn-info         event-authn-info
                                                 :acl                {:owners ["group/nuvla-admin" user-id]}})

              token       (get-in resp [:response :cookies authn-cookie :value])
              cookie-info (if token (sign/unsign-cookie-info token) {})

              uri         (-> resp
                              (ltu/location))
              abs-uri     (str p/service-context uri)]

          ;; check cookie-info in cookie
          (is (= "user/abcdef01-abcd-abcd-abcd-abcdef012345" (:user-id cookie-info)))
          (is (= (str/join " " ["group/nuvla-anon" "group/nuvla-user" uri]) (:claims cookie-info))) ;; uri is also session id
          (is (= uri (:session cookie-info)))               ;; uri is also session id
          (is (not (nil? (:exp cookie-info))))

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
              (header authn-info-header (str "user/user group/nuvla-user " id))
              (request abs-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-id id)
              (ltu/is-operation-present :delete)
              (ltu/is-operation-absent :edit))

          ;; user query with session role should succeed but and have one entry
          (-> (session app)
              (header authn-info-header (str "user/user group/nuvla-user " id))
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 1))

          ;; check contents of session resource
          (let [{:keys [name description tags]} (-> (session app)
                                                    (header authn-info-header (str "user/user group/nuvla-user " id))
                                                    (request abs-uri)
                                                    (ltu/body->edn)
                                                    :response
                                                    :body)]
            (is (= name name-attr))
            (is (= description description-attr))
            (is (= tags tags-attr)))

          ;; user with session role can delete resource
          (-> (session app)
              (header authn-info-header (str "user/user group/nuvla-user " id))
              (request abs-uri
                       :request-method :delete)
              (ltu/is-unset-cookie)
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; create with invalid template fails
          (-> session-anon
              (request base-uri
                       :request-method :post
                       :body (j/write-value-as-string invalid-create))
              (ltu/body->edn)
              (ltu/is-status 400)))))))
