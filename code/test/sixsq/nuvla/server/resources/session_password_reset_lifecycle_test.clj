(ns sixsq.nuvla.server.resources.session-password-reset-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-password-lifecycle-test :as password-test]
    [sixsq.nuvla.server.resources.session-template :as st]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context session/resource-type))


(def session-template-base-uri (str p/service-context st/resource-type))

(deftest lifecycle

  (let [reset-link       (atom nil)
        app              (ltu/ring-app)
        session-json     (content-type (session app) "application/json")
        session-anon     (header session-json authn-info-header "user/unknown group/nuvla-anon")
        session-admin    (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        href             (str st/resource-type "/password-reset")

        template-url     (str p/service-context href)

        name-attr        "name"
        description-attr "description"
        tags-attr        ["one", "two"]]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body] :as message}]
                                        (let [url (second (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*" body))]
                                          (reset! reset-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      ;; password reset session template should exist
      (-> session-anon
          (request template-url)
          (ltu/body->edn)
          (ltu/is-status 200))


      ; anon without valid user can not create session
      (let [username            "anon"
            plaintext-password  "anon"

            valid-create        {:name        name-attr
                                 :description description-attr
                                 :tags        tags-attr
                                 :template    {:href         href
                                               :username     username
                                               :new-password plaintext-password}}
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
            (ltu/is-status 400)
            (ltu/is-key-value :message hashed-password/acceptable-password-msg))
        )


      ;; anon with valid activated user can create session via password reset

      (let [username              "user/jane"
            plaintext-password    "JaneJane-0"
            jane-user-id          (password-test/create-user session-admin
                                                             :username username
                                                             :password plaintext-password
                                                             :activated? true
                                                             :email "jane@example.org")
            {:keys [credential-password] :as jane-user} (-> session-admin
                                                            (request (str p/service-context jane-user-id))
                                                            (ltu/body->edn)
                                                            :response
                                                            :body)
            jane-credential       (-> session-admin
                                      (request (str p/service-context credential-password))
                                      (ltu/body->edn)
                                      :response
                                      :body)

            new-password          "JaneJane-0-changed"
            valid-create          {:name        name-attr
                                   :description description-attr
                                   :tags        tags-attr
                                   :template    {:href         href
                                                 :username     username
                                                 :new-password new-password}}

            redirect-uri          "http://redirect.example.org"

            valid-create-redirect (assoc-in valid-create [:template :redirect-url] redirect-uri)]

        ; anonymous create must succeed; also with redirect

        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str valid-create))
            (ltu/body->edn)
            (ltu/is-status 201))

        ; no changes done on jane credential
        (is (= jane-credential (-> session-admin
                                   (request (str p/service-context credential-password))
                                   (ltu/body->edn)
                                   :response
                                   :body)))

        (-> session-anon
            (request @reset-link)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-set-cookie))

        ; jane credential updated after callback reset-link is executed
        (is (not= jane-credential (-> session-admin
                                      (request (str p/service-context credential-password))
                                      (ltu/body->edn)
                                      :response
                                      :body)))

        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str valid-create-redirect))
            (ltu/body->edn)
            (ltu/is-status 201))

        (-> session-anon
            (request @reset-link)
            (ltu/body->edn)
            (ltu/is-status 303)
            (ltu/is-set-cookie)
            (ltu/is-location-value redirect-uri))))
    ))
