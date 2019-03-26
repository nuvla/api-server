(ns sixsq.nuvla.server.resources.session-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [postal.core :as postal]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as ct]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context session/resource-type))


(def session-template-base-uri (str p/service-context ct/resource-type))


(defn create-user
  [session-admin & {:keys [username password email activated?]}]
  (let [validation-link (atom nil)
        href (str user-tpl/resource-type "/" email-password/registration-method)
        href-create {:template {:href              href
                                :password          password
                                :password-repeated password
                                :username          username
                                :email             email}}]

    (with-redefs [email-utils/smtp-cfg (fn []
                                         {:host "smtp@example.com"
                                          :port 465
                                          :ssl  true
                                          :user "admin"
                                          :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body] :as message}]
                                        (let [url (second (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*" body))]
                                          (reset! validation-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [user-id (-> session-admin
                        (request (str p/service-context user/resource-type)
                                 :request-method :post
                                 :body (json/write-str href-create))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))]

        (when activated?
          (is (re-matches #"^email.*successfully validated$"
                          (-> session-admin
                              (request @validation-link)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              :response
                              :body
                              :message))))
        user-id))))

(deftest lifecycle

  (let [app (ltu/ring-app)
        session-json (content-type (session app) "application/json")
        session-anon (header session-json authn-info-header "user/unknown group/nuvla-anon")
        session-user (header session-json authn-info-header "user group/nuvla-user")
        session-admin (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        href (str st/resource-type "/password")

        template-url (str p/service-context href)

        name-attr "name"
        description-attr "description"
        tags-attr ["one", "two"]]

    ;; password session template should exist
    (-> session-anon
        (request template-url)
        (ltu/body->edn)
        (ltu/is-status 200))


    ;; anon without valid user can not create session
    (let [username "anon"
          plaintext-password "anon"

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href     href
                                      :username username
                                      :password plaintext-password}}
          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")
          unauthorized-create (update-in valid-create [:template :password] (constantly "BAD"))]

      ; anonymous query should succeed but have no entries
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      ; unauthorized create must return a 403 response
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str unauthorized-create))
          (ltu/body->edn)
          (ltu/is-status 403))

      ; unauthorized create with redirect must return a 303
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-create-redirect))
          (ltu/body->edn)
          (ltu/is-status 303)
          (ltu/is-location-value "http://redirect.example.org?error=invalid%20credentials%20for%20%27anon%27"))
      )


    ;; anon with valid activated user can create session
    (let [username "user/jane"
          plaintext-password "JaneJane-0"

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href     href
                                      :username username
                                      :password plaintext-password}}

          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")
          unauthorized-create (update-in valid-create [:template :password] (constantly "BAD"))
          invalid-create (assoc-in valid-create [:template :invalid] "BAD")
          invalid-create-redirect (assoc-in valid-create-redirect [:template :invalid] "BAD")
          jane-user-id (create-user session-admin
                                    :username username
                                    :password plaintext-password
                                    :activated? true
                                    :email "jane@example.org")
          ]

      ; unauthorized create must return a 403 response
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str unauthorized-create))
          (ltu/body->edn)
          (ltu/is-status 403))

      ; anonymous create must succeed; also with redirect
      (let [resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str valid-create))
                     (ltu/body->edn)
                     (ltu/is-set-cookie)
                     (ltu/is-status 201))
            id (get-in resp [:response :body :resource-id])

            token (get-in resp [:response :cookies "com.sixsq.nuvla.cookie" :value :token])
            claims (if token (sign/unsign-claims token) {})

            uri (ltu/location resp)
            abs-uri (str p/service-context uri)

            resp2 (-> session-anon
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-create-redirect))
                      (ltu/body->edn)
                      (ltu/is-set-cookie)
                      (ltu/is-status 303))
            id2 (get-in resp2 [:response :body :resource-id])

            token2 (get-in resp2 [:response :cookies "com.sixsq.nuvla.cookie" :value :token])
            claims2 (if token2 (sign/unsign-claims token2) {})

            uri2 (ltu/location resp2)]

        ; check claims in cookie
        (is (= jane-user-id (:username claims)))
        (is (= #{"group/nuvla-user"
                 "group/nuvla-anon"
                 uri}
               (-> claims
                   :roles
                   (str/split #"\s")
                   set)))
        (is (= uri (:session claims)))
        (is (not (nil? (:exp claims))))

        ; check claims in cookie for redirect
        (is (= jane-user-id (:username claims2)))
        (is (= #{"group/nuvla-user"
                 "group/nuvla-anon"
                 id2}
               (-> claims2
                   :roles
                   (str/split #"\s")
                   set)))
        (is (= id2 (:session claims2)))
        (is (not (nil? (:exp claims2))))
        (is (= "http://redirect.example.org" uri2))

        ; user should not be able to see session without session role
        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403))

        ; anonymous query should succeed but still have no entries
        (-> session-anon
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        ; user query should succeed but have no entries because of missing session role
        (-> session-user
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        ; admin query should succeed, but see no sessions without the correct session role
        (-> session-admin
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 0))

        ; user should be able to see session with session role
        (-> (session app)
            (header authn-info-header (str "user/user group/nuvla-user " id))
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))

        ; check contents of session
        (let [{:keys [name description tags] :as body} (-> session-user
                                                           (header authn-info-header (str "user/user group/nuvla-user group/nuvla-anon " id))
                                                           (request abs-uri)
                                                           (ltu/body->edn)
                                                           :response
                                                           :body)]
          (is (= name name-attr))
          (is (= description description-attr))
          (is (= tags tags-attr)))

        ; user query with session role should succeed but and have one entry
        (-> (session app)
            (header authn-info-header (str "user/user group/nuvla-user " id))
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))

        ; user with session role can delete resource
        (-> (session app)
            (header authn-info-header (str "user/user group/nuvla-user " id))
            (request abs-uri
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200))

        ; create with invalid template fails
        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-create))
            (ltu/body->edn)
            (ltu/is-status 400)))

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
          (ltu/is-status 303))
      )

    ;; anon with valid non activated user cannot create session
    (let [username "alex"
          plaintext-password "AlexAlex-0"

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href     href
                                      :username username
                                      :password plaintext-password}}

          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")]

      (create-user session-admin
                   :username username
                   :password plaintext-password
                   :activated? false
                   :email "alex@example.org")

      ; unauthorized create must return a 403 response
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-create))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ))

