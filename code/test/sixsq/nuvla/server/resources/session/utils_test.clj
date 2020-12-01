(ns sixsq.nuvla.server.resources.session.utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.session.utils :as session-utils]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context session/resource-type))


(def session-template-base-uri (str p/service-context st/resource-type))


(defn create-user
  [session-admin & {:keys [username password email activated?]}]
  (let [validation-link (atom nil)
        href            (str user-tpl/resource-type "/" email-password/registration-method)
        href-create     {:template {:href     href
                                    :password password
                                    :username username
                                    :email    email}}]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (let [url (->> body second :content
                                                       (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*")
                                                       second)]
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
                              (ltu/body)
                              :message))))
        user-id))))


;; verifies that a session can be updated internally
;; session setup is taken from the password session lifecycle test
(deftest check-session-update

  (let [app              (ltu/ring-app)
        session-json     (content-type (session app) "application/json")
        session-admin    (header session-json authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon     (header session-json authn-info-header "user/unknown group/nuvla-anon")
        session-user     (header session-json authn-info-header "user group/nuvla-user")

        href             (str st/resource-type "/password")

        template-url     (str p/service-context href)

        name-attr        "name"
        description-attr "description"
        tags-attr        ["one", "two"]]

    ;; password session template must exist
    (-> session-anon
        (request template-url)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; anon with valid activated user can create session
    (let [username           "user/jane"
          plaintext-password "JaneJane-0"

          valid-create       {:name        name-attr
                              :description description-attr
                              :tags        tags-attr
                              :template    {:href     href
                                            :username username
                                            :password plaintext-password}}]

      ;; create a user
      (create-user session-admin
                   :username username
                   :password plaintext-password
                   :activated? true
                   :email "jane@example.org")

      ; anonymous create must succeed
      (let [resp    (-> session-anon
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str valid-create))
                        (ltu/body->edn)
                        (ltu/is-set-cookie)
                        (ltu/is-status 201))
            id      (ltu/body-resource-id resp)

            abs-url (ltu/location-url resp)

            {:keys [name description tags] :as original-session}
            (-> session-user
                (header authn-info-header (str "user/user group/nuvla-user group/nuvla-anon " id))
                (request abs-url)
                (ltu/body->edn)
                :response
                :body)]

        ; check contents of session
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))

        ;; After the setup, NOW verify that the session can be updated!
        (let [new-name        "UPDATED SESSION NAME"
              correct-session (assoc original-session :name new-name)]

          (session-utils/update-session (:id original-session) correct-session)

          (let [updated-session (-> session-user
                                    (header authn-info-header (str "user/user group/nuvla-user group/nuvla-anon " id))
                                    (request abs-url)
                                    (ltu/body->edn)
                                    :response
                                    :body)]

            (is (= new-name (:name updated-session)))
            (is (not= (:updated original-session) (:updated updated-session)))
            (is (= (dissoc correct-session :updated)
                   (dissoc updated-session :updated :updated-by)))))))))

