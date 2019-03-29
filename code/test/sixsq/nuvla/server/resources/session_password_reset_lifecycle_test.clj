(ns sixsq.nuvla.server.resources.session-password-reset-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-password-lifecycle-test :as password-test]
    [sixsq.nuvla.server.resources.session-template :as st]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context session/resource-type))


(def session-template-base-uri (str p/service-context st/resource-type))

(deftest lifecycle

  (let [app (ltu/ring-app)
        session-json (content-type (session app) "application/json")
        session-anon (header session-json authn-info-header "user/unknown group/nuvla-anon")
        session-user (header session-json authn-info-header "user group/nuvla-user")
        session-admin (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        href (str st/resource-type "/password-reset")

        template-url (str p/service-context href)

        name-attr "name"
        description-attr "description"
        tags-attr ["one", "two"]]

    ;; password reset session template should exist
    (-> session-anon
        (request template-url)
        (ltu/body->edn)
        (ltu/is-status 200))


    ; anon without valid user can not create session
    (let [username "anon"
          plaintext-password "anon"

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href         href
                                      :username     username
                                      :new-password plaintext-password}}
          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")
          unauthorized-create (update-in valid-create [:template :new-password] (constantly "BAD"))]

      ; anonymous query should succeed but have no entries
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      ; password not acceptable return a 400 response
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str unauthorized-create))
          (ltu/body->edn)
          (ltu/is-status 400))

      ; unauthorized create with redirect must return a 303
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-create-redirect))
          (ltu/body->edn)
          (ltu/is-status 303)
          (ltu/is-location-value "http://redirect.example.org?error=password%20must%20contain%20at%20least%20one%20uppercase%20character%2C%20one%20lowercase%20character%2C%20one%20digit%2C%20one%20special%20character%2C%20and%20at%20least%208%20characters%20in%20total"))
      )


    ;; anon with valid activated user can create session via password reset
    (let [username "user/jane"
          plaintext-password "JaneJane-0"

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href         href
                                      :username     username
                                      :new-password plaintext-password}}

          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")
          unauthorized-create (update-in valid-create [:template :new-password] (constantly "BAD"))
          invalid-create (assoc-in valid-create [:template :invalid] "BAD")
          invalid-create-redirect (assoc-in valid-create-redirect [:template :invalid] "BAD")
          jane-user-id (password-test/create-user session-admin
                                                  :username username
                                                  :password plaintext-password
                                                  :activated? true
                                                  :email "jane@example.org")]

      ; anonymous create must succeed; also with redirect

      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-create))
          (ltu/body->edn)
          (ltu/dump)
          (ltu/is-status 201))
      #_(let [resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str valid-create))
                     (ltu/body->edn)
                     (ltu/is-set-cookie)
                     (ltu/is-status 201))

            id (get-in resp [:response :body :resource-id])

            token (get-in resp [:response :cookies "com.sixsq.nuvla.cookie" :value])
            authn-info (if token (sign/unsign-cookie-info token) {})

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

            token2 (get-in resp2 [:response :cookies "com.sixsq.nuvla.cookie" :value])
            authn-info2 (if token2 (sign/unsign-cookie-info token2) {})

            uri2 (ltu/location resp2)
            ]

        ; check claims in cookie
        (is (= jane-user-id (:user-id authn-info)))
        (is (= #{"group/nuvla-user"
                 "group/nuvla-anon"
                 uri}
               (-> authn-info
                   :claims
                   (str/split #"\s")
                   set)))
        (is (= uri (:session authn-info)))
        (is (not (nil? (:exp authn-info))))

        ; check claims in cookie for redirect
        (is (= jane-user-id (:user-id authn-info2)))
        (is (= #{"group/nuvla-user"
                 "group/nuvla-anon"
                 id2}
               (-> authn-info2
                   :claims
                   (str/split #"\s")
                   set)))
        (is (= id2 (:session authn-info2)))
        (is (not (nil? (:exp authn-info2))))
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
      ;(-> session-admin
      ;    (request base-uri
      ;             :request-method :post
      ;             :body (json/write-str invalid-create))
      ;    (ltu/body->edn)
      ;    (ltu/is-status 400))
      ;
      ;(-> session-admin
      ;    (request base-uri
      ;             :request-method :post
      ;             :body (json/write-str invalid-create-redirect))
      ;    (ltu/body->edn)
      ;    (ltu/is-status 303))
      )

    ;; anon with valid non activated user cannot create session
    #_(let [username "alex"
          plaintext-password "AlexAlex-0"

          valid-create {:name        name-attr
                        :description description-attr
                        :tags        tags-attr
                        :template    {:href         href
                                      :username     username
                                      :new-password plaintext-password}}

          valid-create-redirect (assoc-in valid-create [:template :redirectURI] "http://redirect.example.org")]

      (password-test/create-user session-admin
                                 :username username
                                 :new-password plaintext-password
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

