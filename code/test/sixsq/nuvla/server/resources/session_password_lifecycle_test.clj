(ns sixsq.nuvla.server.resources.session-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.auth.utils.sign :as sign]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info
     :refer [authn-cookie authn-info-header wrap-authn-info]]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.email.sending :as email-sending]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.group-template :as group-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
    [sixsq.nuvla.server.resources.session :as session]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context session/resource-type))
(def grp-base-uri (str p/service-context group/resource-type))
(def nb-base-uri (str p/service-context nuvlabox/resource-type))

(defn create-user
  [session-admin & {:keys [username password email activated?]}]
  (let [validation-link (atom nil)
        href            (str user-tpl/resource-type "/" email-password/registration-method)
        href-create     {:template {:href     href
                                    :password password
                                    :username username
                                    :email    email}}]

    (with-redefs [email-sending/extract-smtp-cfg
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

(defn valid-create-grp
  [group-id]
  {:template {:href             "group-template/generic"
              :group-identifier group-id
              :name             (str "Group " group-id)
              :description      (str "Group " group-id " description")}})

(deftest lifecycle

  (let [app              (ltu/ring-app)
        session-json     (content-type (session app) "application/json")
        session-anon     (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
        session-user     (header session-json authn-info-header "user group/nuvla-user")
        session-admin    (header session-json authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

        href             (str st/resource-type "/password")

        template-url     (str p/service-context href)

        name-attr        "name"
        description-attr "description"
        tags-attr        ["one", "two"]]

    ;; password session template should exist
    (-> session-anon
        (request template-url)
        (ltu/body->edn)
        (ltu/is-status 200))


    ;; anon without valid user can not create session
    (let [username            "anon"
          plaintext-password  "anon"

          valid-create        {:name        name-attr
                               :description description-attr
                               :tags        tags-attr
                               :template    {:href     href
                                             :username username
                                             :password plaintext-password}}
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
      )


    ;; anon with valid activated user can create session
    (let [username           "user/jane"
          plaintext-password "JaneJane-0"

          valid-create       {:name        name-attr
                              :description description-attr
                              :tags        tags-attr
                              :template    {:href     href
                                            :username username
                                            :password plaintext-password}}

          invalid-create     (assoc-in valid-create [:template :invalid] "BAD")
          jane-user-id       (create-user session-admin
                                          :username username
                                          :password plaintext-password
                                          :activated? true
                                          :email "jane@example.org")]

      ; anonymous create must succeed
      (let [resp             (-> session-anon
                                 (request base-uri
                                          :request-method :post
                                          :body (json/write-str valid-create))
                                 (ltu/body->edn)
                                 (ltu/is-set-cookie)
                                 (ltu/is-status 201))
            id               (ltu/body-resource-id resp)

            credential-id    (:credential-password (auth-password/user-id->user jane-user-id))
            _                (ltu/is-last-event id
                                                {:name               "session.add"
                                                 :description        (str username " logged in")
                                                 :category           "add"
                                                 :success            true
                                                 :linked-identifiers [jane-user-id credential-id]
                                                 :authn-info         {:user-id      "user/unknown"
                                                                      :active-claim "user/unknown"
                                                                      :claims       ["user/unknown" "group/nuvla-anon"]}
                                                 :acl                {:owners ["group/nuvla-admin" jane-user-id]}})

            token            (get-in resp [:response :cookies authn-cookie :value])
            authn-info       (if token (sign/unsign-cookie-info token) {})
            event-authn-info {:user-id      "user/user"
                              :active-claim "group/nuvla-user"
                              :claims       ["group/nuvla-anon" id "user/user"]}

            uri              (ltu/location resp)
            abs-uri          (str p/service-context uri)]

        ; check claims in cookie
        (is (= jane-user-id (:user-id authn-info)))
        (is (= #{"group/nuvla-user"
                 "group/nuvla-anon"
                 uri
                 jane-user-id}
               (some-> authn-info
                       :claims
                       (str/split #"\s")
                       set)))
        (is (= uri (:session authn-info)))
        (is (not (nil? (:exp authn-info))))

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
            (ltu/is-operation-present :delete)
            (ltu/is-operation-absent :edit)
            (ltu/is-operation-present :switch-group))

        ; check contents of session
        (let [{:keys [name description tags]} (-> session-user
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

        (ltu/is-last-event id
                           {:name               "session.delete"
                            :description        (str "user/user logged out")
                            :category           "delete"
                            :success            true
                            :linked-identifiers ["user/user"]
                            :authn-info         event-authn-info
                            :acl                {:owners ["group/nuvla-admin"
                                                          "user/user"]}})

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
          (ltu/is-status 400)))

    ;; anon with valid non activated user cannot create session
    (let [username           "alex"
          plaintext-password "AlexAlex-0"

          valid-create       {:name        name-attr
                              :description description-attr
                              :tags        tags-attr
                              :template    {:href     href
                                            :username username
                                            :password plaintext-password}}]

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
          (ltu/is-status 403)))))


(deftest switch-group-lifecycle-test
  (let [app                (ltu/ring-app)
        session-json       (content-type (session app) "application/json")
        session-anon       (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
        session-admin      (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon group/nuvla-admin")

        href               (str st/resource-type "/password")

        username           "user/bob"
        plaintext-password "BobBob-0"
        user-id            (create-user session-admin
                                        :username username
                                        :password plaintext-password
                                        :activated? true
                                        :email "bob@example.org")

        valid-create       {:template {:href     href
                                       :username username
                                       :password plaintext-password}}
        session-user       (-> session-anon
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str valid-create))
                               (ltu/body->edn)
                               (ltu/is-set-cookie)
                               (ltu/is-status 201))
        session-user-id    (ltu/body-resource-id session-user)
        sesssion-user-url  (ltu/location-url session-user)
        credential-id      (:credential-password (auth-password/user-id->user user-id))
        _                  (ltu/is-last-event session-user-id
                                              {:name               "session.add"
                                               :description        (str username " logged in")
                                               :category           "add"
                                               :success            true
                                               :linked-identifiers [user-id credential-id]
                                               :authn-info         {:user-id      "user/unknown"
                                                                    :active-claim "user/unknown"
                                                                    :claims       ["user/unknown" "group/nuvla-anon"]}
                                               :acl                {:owners ["group/nuvla-admin" user-id]}})
        handler            (wrap-authn-info identity)
        authn-session-user (-> session-user
                               :response
                               (select-keys [:cookies])
                               handler
                               seq
                               flatten)
        group-a-identifier "switch-test-a"
        group-a            (str group/resource-type "/" group-a-identifier)
        group-b-identifier "switch-test-b"
        group-b            (str group/resource-type "/" group-b-identifier)
        switch-op-url      (-> (apply request session-json (concat [sesssion-user-url] authn-session-user))
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url :switch-group))
        event-authn-info   {:user-id      user-id
                            :active-claim user-id
                            :claims       ["group/nuvla-anon" "group/nuvla-user" session-user-id user-id]}]

    (testing "User cannot switch to a group that he is not part of."
      (-> (apply request session-json
                 (concat [switch-op-url :body (json/write-str {:claim group-b})
                          :request-method :post] authn-session-user))
          (ltu/body->edn)
          (ltu/is-status 403)
          (ltu/message-matches #"Switch group cannot be done to requested group:.*"))
      (ltu/is-last-event session-user-id {:name               "session.switch-group"
                                          :description        "Switch group attempt failed"
                                          :category           "action"
                                          :success            false
                                          :linked-identifiers [group-b]
                                          :authn-info         event-authn-info
                                          :acl                {:owners ["group/nuvla-admin" group-b user-id]}}))

    (testing "User can switch to a group that he is part of."
      (-> session-admin
          (request (-> session-admin
                       (request (str p/service-context group/resource-type)
                                :request-method :post
                                :body (json/write-str
                                        {:template
                                         {:href             (str group-tpl/resource-type "/generic")
                                          :group-identifier group-a-identifier}}))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location-url))
                   :request-method :put
                   :body (json/write-str {:users [user-id]}))
          (ltu/body->edn)
          (ltu/is-status 200))
      (let [response              (-> (apply request session-json
                                             (concat [switch-op-url :body (json/write-str {:claim group-a})
                                                      :request-method :post] authn-session-user))
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-set-cookie)
                                      :response)
            authn-session-group-a (-> response
                                      (select-keys [:cookies])
                                      handler
                                      seq
                                      flatten)]
        (testing "Cookie is set and claims correspond to group a"
          (is (= {:active-claim group-a
                  :claims       #{"group/nuvla-anon"
                                  "group/nuvla-user"
                                  session-user-id
                                  group-a}
                  :user-id      user-id}
                 (-> response
                     handler
                     auth/current-authentication))))

        (testing "Nuvlabox owner is set correctly to the active-claim"
          (binding [config-nuvla/*stripe-api-key* nil]
            (let [nuvlabox-url (-> (apply request session-json
                                          (concat [nb-base-uri
                                                   :body (json/write-str {})
                                                   :request-method :post] authn-session-group-a))
                                   (ltu/body->edn)
                                   (ltu/is-status 201)
                                   (ltu/location-url))]

              (-> (apply request session-json (concat [nuvlabox-url] authn-session-group-a))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :owner group-a)))))

        (testing "switch back to user is possible"
          (is (= user-id
                 (-> (apply request session-json
                            (concat [switch-op-url :body (json/write-str {:claim user-id})
                                     :request-method :post] authn-session-group-a))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/is-set-cookie)
                     :response
                     (select-keys [:cookies])
                     handler
                     auth/current-authentication
                     :active-claim))))

        (testing "switch to subgroup is possible"
          (-> (header session-json authn-info-header (str "user/x " group-a " user/x group/nuvla-user group/nuvla-anon " group-a))
              (request grp-base-uri
                       :request-method :post
                       :body (json/write-str (valid-create-grp "switch-test-b")))
              (ltu/body->edn)
              (ltu/is-status 201))

          (let [response              (-> (apply request session-json
                                                 (concat [switch-op-url :body (json/write-str {:claim "group/switch-test-b"})
                                                          :request-method :post] authn-session-user))
                                          (ltu/body->edn)
                                          (ltu/is-status 200)
                                          (ltu/is-set-cookie)
                                          :response)
                authn-session-group-b (-> response
                                          (select-keys [:cookies])
                                          handler
                                          seq
                                          flatten)]
            (is (= "group/switch-test-b"
                   (-> response
                       (select-keys [:cookies])
                       handler
                       auth/current-authentication
                       :active-claim)))

            (-> (apply request session-json (concat [(str p/service-context nuvlabox/resource-type)] authn-session-group-b))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 0))

            (-> (apply request session-json
                       (concat [nb-base-uri
                                :body (json/write-str {})
                                :request-method :post] authn-session-group-b))
                (ltu/body->edn)
                (ltu/is-status 201)))))
      (testing "switch to subgroup with extended claims"
        (let [response                  (-> (apply request session-json
                                                   (concat [switch-op-url :body (json/write-str {:claim group-a :extended true})
                                                            :request-method :post] authn-session-user))
                                            (ltu/body->edn)
                                            (ltu/is-status 200)
                                            (ltu/is-set-cookie)
                                            :response)
              authn-session-group-a-ext (-> response
                                            (select-keys [:cookies])
                                            handler
                                            seq
                                            flatten)]
          (testing "Cookie is set and claims correspond to group a but claims are extended"
            (is (= {:active-claim group-a
                    :claims       #{"group/nuvla-anon"
                                    "group/nuvla-user"
                                    session-user-id
                                    group-a
                                    group-b}
                    :user-id      user-id}
                   (-> response
                       handler
                       auth/current-authentication))))

          (testing "NuvlaEdge of group b are visible for group a"
            (-> (apply request session-json
                       (concat [nb-base-uri] authn-session-group-a-ext))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 2))))))))


(deftest get-groups-lifecycle-test
  (let [app             (ltu/ring-app)
        session-json    (content-type (session app) "application/json")
        session-anon    (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
        session-admin   (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon group/nuvla-admin")
        user-id         (create-user session-admin
                                     :username "tarzan"
                                     :password "TarzanTarzan-0"
                                     :activated? true
                                     :email "tarzan@example.org")
        session-user    (header session-json authn-info-header (str user-id user-id " group/nuvla-user group/nuvla-anon"))
        session-group-a (header session-json authn-info-header "user/x group/a user/x group/nuvla-user group/nuvla-anon group/a")
        session-group-b (header session-json authn-info-header "user/x group/b user/x group/nuvla-user group/nuvla-anon group/b")
        href            (str st/resource-type "/password")]

    (-> session-admin
        (request grp-base-uri
                 :request-method :post
                 :body (json/write-str (valid-create-grp "a")))
        (ltu/body->edn)
        (ltu/is-status 201))
    (-> session-group-a
        (request grp-base-uri
                 :request-method :post
                 :body (json/write-str (valid-create-grp "b")))
        (ltu/body->edn)
        (ltu/is-status 201))
    (-> session-group-a
        (request grp-base-uri
                 :request-method :post
                 :body (json/write-str (valid-create-grp "b1")))
        (ltu/body->edn)
        (ltu/is-status 201))
    (-> session-group-b
        (request grp-base-uri
                 :request-method :post
                 :body (json/write-str (valid-create-grp "c")))
        (ltu/body->edn)
        (ltu/is-status 201))

    (let [resp            (-> session-anon
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str {:template {:href     href
                                                                         :username "tarzan"
                                                                         :password "TarzanTarzan-0"}}))
                              (ltu/body->edn)
                              (ltu/is-set-cookie)
                              (ltu/is-status 201))
          id              (ltu/body-resource-id resp)
          abs-uri         (ltu/location-url resp)
          session-with-id (header session-json authn-info-header (str user-id user-id " group/nuvla-user group/nuvla-anon " id))]
      (testing "User should be able to see session with session role"
        (-> session-with-id
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-absent :edit)
            (ltu/is-operation-present :switch-group)
            (ltu/is-operation-present :get-peers)
            (ltu/is-operation-present :get-groups)))

      (let [get-groups-url (-> session-user
                               (header authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon " id))
                               (request abs-uri)
                               (ltu/body->edn)
                               (ltu/get-op-url :get-groups))]

        (testing "User who is not in any group should get empty list of groups"
          (-> session-with-id
              (request get-groups-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/body)
              (= [])
              (is "Get groups body should have no childs")))

        (testing "When user is part of a group, he should get subgroups"
          (-> session-admin
              (request (str p/service-context "group/b")
                       :request-method :put
                       :body (json/write-str {:users [user-id]}))
              (ltu/body->edn)
              (ltu/is-status 200))
          (-> session-with-id
              (request get-groups-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/body)
              (= [{:children    [{:description "Group c description"
                                  :id          "group/c"
                                  :name        "Group c"}]
                   :description "Group b description"
                   :id          "group/b"
                   :name        "Group b"}])
              (is "User get group/b and subgroup group/c")))

        (testing "When user is part of a root group he should get
          the full group hierarchy and group/b is not duplicated"
          (-> session-admin
              (request (str p/service-context "group/a")
                       :request-method :put
                       :body (json/write-str {:users [user-id]}))
              (ltu/body->edn)
              (ltu/is-status 200))
          (-> session-admin
              (request grp-base-uri
                       :request-method :post
                       :body (json/write-str (valid-create-grp "z")))
              (ltu/body->edn)
              (ltu/is-status 201))
          (-> session-admin
              (request (str p/service-context "group/z")
                       :request-method :put
                       :body (json/write-str {:users [user-id]}))
              (ltu/body->edn)
              (ltu/is-status 200))
          (-> session-with-id
              (request get-groups-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/body)
              (= [{:children    [{:children    [{:description "Group c description"
                                                 :id          "group/c"
                                                 :name        "Group c"}]
                                  :description "Group b description"
                                  :id          "group/b"
                                  :name        "Group b"}
                                 {:description "Group b1 description"
                                  :id          "group/b1"
                                  :name        "Group b1"}]
                   :description "Group a description"
                   :id          "group/a"
                   :name        "Group a"}
                  {:description "Group z description"
                   :id          "group/z"
                   :name        "Group z"}])
              (is "Get groups body should contain tree of groups")))))))


(deftest get-peers-lifecycle-test
  (let [app             (ltu/ring-app)
        session-json    (content-type (session app) "application/json")
        session-anon    (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
        session-admin   (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon group/nuvla-admin")
        user-id         (create-user session-admin
                                     :username "peer0"
                                     :password "Peer0Peer-0"
                                     :activated? true
                                     :email "peer-0@example.org")
        peer-1          (create-user session-admin
                                     :username "peer1"
                                     :password "Peer1Peer-1"
                                     :activated? true
                                     :email "peer-1@example.org")
        peer-2          (create-user session-admin
                                     :username "peer2"
                                     :password "Peer2Peer-2"
                                     :activated? false
                                     :email "peer-2@example.org")
        peer-3          (create-user session-admin
                                     :username "peer3"
                                     :password "Peer3Peer-3"
                                     :activated? true
                                     :email "peer-3@example.org")
        session-user    (header session-json authn-info-header (str user-id user-id " group/nuvla-user group/nuvla-anon"))
        session-group-a (header session-json authn-info-header "user/x group/peers-test-a user/x group/nuvla-user group/nuvla-anon group/peers-test-a")
        href            (str st/resource-type "/password")

        resp            (-> session-anon
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str {:template {:href     href
                                                                       :username "peer0"
                                                                       :password "Peer0Peer-0"}}))
                            (ltu/body->edn)
                            (ltu/is-set-cookie)
                            (ltu/is-status 201))
        id              (ltu/body-resource-id resp)
        abs-uri         (ltu/location-url resp)
        session-with-id (header session-json authn-info-header (str user-id user-id " group/nuvla-user group/nuvla-anon " id))
        get-peers-url   (-> session-user
                            (header authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon " id))
                            (request abs-uri)
                            (ltu/body->edn)
                            (ltu/get-op-url :get-peers))]

    (testing "admin should get all users with validated emails"
      (-> session-admin
          (request get-peers-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body)
          vals
          set
          (= #{"peer-0@example.org" "peer-1@example.org" "peer-3@example.org"})
          (is "Get peers body should contain all users with validated emails")))

    (testing "user who is not in any group should get empty map of peers"
      (-> session-with-id
          (request get-peers-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body)
          (= {})
          (is "Get peers body should be empty")))

    (-> session-admin
        (request (-> session-admin
                     (request grp-base-uri
                              :request-method :post
                              :body (json/write-str (valid-create-grp "peers-test-a")))
                     (ltu/body->edn)
                     (ltu/is-status 201)
                     (ltu/location-url))
                 :request-method :put
                 :body (json/write-str {:users [peer-1 user-id peer-2]}))
        (ltu/body->edn)
        (ltu/is-status 200))

    (testing "user should get peers of the group when email is validated only"
      (-> session-with-id
          (request get-peers-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body)
          vals
          set
          (= #{"peer-0@example.org" "peer-1@example.org"})
          (is "Get peers body should be himself and peer-1")))

    (testing "user should get peers of subgroup also"
      (-> session-admin
          (request (-> session-group-a
                       (request grp-base-uri
                                :request-method :post
                                :body (json/write-str (valid-create-grp "peers-test-b")))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location-url))
                   :request-method :put
                   :body (json/write-str {:users [peer-3 user-id peer-2]}))
          (ltu/body->edn)
          (ltu/is-status 200))
      (-> session-with-id
          (request get-peers-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body)
          vals
          set
          (= #{"peer-0@example.org" "peer-1@example.org" "peer-3@example.org"})
          (is "Get peers body should contain peer-3")))))
