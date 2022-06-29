(ns sixsq.nuvla.server.resources.group-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.resource-creation :as resource-creation]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.group :as t]
    [sixsq.nuvla.server.resources.group-template :as group-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  (let [app                     (ltu/ring-app)
        session-json            (content-type (session app) "application/json")
        session-admin           (header session-json authn-info-header "user/jane group/nuvla-admin group/nuvla-user group/nuvla-anon group/nuvla-admin")
        session-user            (header session-json authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon            (header session-json authn-info-header "group/nuvla-anon")

        href                    (str group-tpl/resource-type "/generic")

        name-attr               "name"
        description-attr        "description"
        tags-attr               ["one", "two"]

        valid-create-id         "alpha-one"
        valid-create            {:name        name-attr
                                 :description description-attr
                                 :tags        tags-attr
                                 :template    {:href             href
                                               :group-identifier valid-create-id}}

        valid-create-no-href-id "beta-two"
        valid-create-no-href    {:name        name-attr
                                 :description description-attr
                                 :tags        tags-attr
                                 :template    {:group-identifier valid-create-no-href-id}}

        tarzan-email            "tarzan@example.com"
        user-tarzan-id          (resource-creation/create-user tarzan-email)]

    ;; admin query should succeed and contains predefined number of entries
    (let [entries (-> session-admin
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count #(>= % (count t/default-groups-users)))
                      (ltu/entries))]
      (is (= (set/intersection #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-nuvlabox"
                                 "group/nuvla-anon" "group/nuvla-vpn"} (set (map :id entries)))
             #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-nuvlabox"
               "group/nuvla-anon" "group/nuvla-vpn"}))
      (is (every? #(not (nil? %)) (set (map :name entries))))
      (is (every? #(not (nil? %)) (set (map :description entries)))))


    ;; user query should also have 5 entries, but only common attributes (i.e. no :users field)
    (let [entries (-> session-user
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count #(>= % (count t/default-groups-users)))
                      (ltu/entries))]
      (is (= (set/intersection #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-nuvlabox"
                                 "group/nuvla-anon" "group/nuvla-vpn"}
                               (set (map :id entries)))
             #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-nuvlabox"
               "group/nuvla-anon" "group/nuvla-vpn"})))

    ;; anon query should see nothing
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; test lifecycle of new group
    (with-redefs [auth-password/invited-by (fn [_] "jane")
                  postal/send-message      (fn [_ _] {:code 0, :error :SUCCESS, :message "OK"})]
      (doseq [session [session-user session-admin]]
        (doseq [tpl [valid-create valid-create-no-href]]
          (let [abs-uri     (-> session
                                (request base-uri
                                         :request-method :post
                                         :body (json/write-str tpl))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location-url))

                expected-id (str "group/" (get-in tpl [:template :group-identifier]))]

            ;; check contents of resource
            (let [{:keys [id] :as body} (-> session
                                            (request abs-uri)
                                            (ltu/body->edn)
                                            (ltu/is-status 200)
                                            (ltu/is-key-value :id expected-id)
                                            (ltu/is-key-value :name name-attr)
                                            (ltu/is-key-value :description description-attr)
                                            (ltu/is-key-value :tags tags-attr)
                                            (ltu/is-key-value :users ["user/jane"])
                                            (ltu/body))
                  ;; actually add some users to the group
                  users [user-tarzan-id
                         "user/bb2f41a3-c54c-fce8-32d2-0324e1c32e22"
                         "user/cc2f41a3-c54c-fce8-32d2-0324e1c32e22"]]
              (-> session
                  (request abs-uri
                           :request-method :put
                           :body (json/write-str (assoc body :users users)))
                  (ltu/body->edn)
                  (ltu/is-status 200))

              (let [response   (-> session
                                   (request abs-uri)
                                   (ltu/body->edn))
                    {updated-users :users
                     acl           :acl} (ltu/body response)
                    invite-url (-> response
                                   (ltu/is-operation-present :invite)
                                   (ltu/get-op-url :invite))]

                (-> session
                    (request invite-url
                             :request :put
                             :body (json/write-str {:username "notexistandnotemail"}))
                    (ltu/body->edn)
                    (ltu/is-status 400)
                    (ltu/message-matches "invalid email"))

                (-> session
                    (request invite-url
                             :request :put
                             :body (json/write-str {:username tarzan-email}))
                    (ltu/body->edn)
                    (ltu/is-status 400)
                    (ltu/message-matches "user already in group"))

                (-> session
                    (request invite-url
                             :request :put
                             :body (json/write-str {:username "max@example.com"}))
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/message-matches (str "successfully invited to " id)))

                (binding [config-nuvla/*authorized-redirect-urls* ["https://nuvla.io"]]
                  (-> session
                      (request invite-url
                               :request :put
                               :body (json/write-str {:username     "jane@example.com"
                                                      :redirect-url "https://phishing.com"}))
                      (ltu/body->edn)
                      (ltu/is-status 400)
                      (ltu/message-matches config-nuvla/error-msg-not-authorised-redirect-url)))

                (is (= users updated-users))
                (is (= (set (conj users id)) (set (remove #{"group/nuvla-admin" "group/nuvla-vpn"} (:view-meta acl)))))))

            ;; delete should work
            (-> session
                (request abs-uri
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200))))))))


(deftest lifecycle-subgroup-creation

  (let [app              (ltu/ring-app)
        session-json     (content-type (session app) "application/json")
        session-admin    (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon group/nuvla-admin")
        session-user     (header session-json authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-group-a  (header session-json authn-info-header "user/jane group/a user/jane group/nuvla-user group/nuvla-anon group/a")
        session-group-b  (header session-json authn-info-header "user/jane group/b user/jane group/nuvla-user group/nuvla-anon group/b")

        href             (str group-tpl/resource-type "/generic")

        name-attr        "name"
        description-attr "description"
        tags-attr        ["one", "two"]

        valid-create     (fn [group-id] {:name        name-attr
                                         :description description-attr
                                         :tags        tags-attr
                                         :template    {:href             href
                                                       :group-identifier group-id}})]

    (testing "A user should be able to create a group and see it"
      (let [abs-uri (-> session-user
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str (valid-create "a")))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location-url))]
        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :parents nil))))

    (testing "A group should be able to create a subgroup and see it"
      (let [abs-uri (-> session-group-a
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str (valid-create "b")))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location-url))]
        (-> session-group-a
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :parents ["group/a"]))
        (testing "subgroup is able to see himself"
          (-> session-group-b
              (request abs-uri)
              (ltu/body->edn)
              (ltu/is-status 200)))))

    (testing "A group should be able to create a subgroup and see it with all parents"
      (let [abs-uri (-> session-group-b
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str (valid-create "c")))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location-url))]
        (-> session-group-b
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :parents ["group/a" "group/b"]))

        (testing "parents field cannot be updated"
          (-> session-admin
              (request abs-uri
                       :request-method :put
                       :body (json/write-str {:parents ["change-not-allowed"]}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :parents ["group/a" "group/b"]))
          (-> session-admin
              (request (str abs-uri "?select=parents")
                       :request-method :put
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :parents ["group/a" "group/b"])))))

    (testing "A user should not be able to create the 19th group of a group"
      (let [session-group-d (header session-json authn-info-header
                                    "user/jane group/d user/jane group/nuvla-user group/nuvla-anon group/d")]
        (-> session-user
            (request base-uri
                     :request-method :post
                     :body (json/write-str (valid-create "d")))
            ltu/body->edn
            (ltu/is-status 201))
        (doseq [group-idx (range 19)]
          (-> session-group-d
              (request base-uri
                       :request-method :post
                       :body (json/write-str (valid-create (str "d" group-idx))))
              ltu/body->edn
              (ltu/is-status 201)))
        (-> session-group-d
            (request (str base-uri "?filter=parents='group/d'&last=0")
                     :request-method :put)
            ltu/body->edn
            (ltu/is-status 200))
        (-> session-group-d
            (request base-uri
                     :request-method :post
                     :body (json/write-str (valid-create "d-unwanted1")))
            ltu/body->edn
            (ltu/is-status 409)
            (ltu/message-matches "A group cannot have more than 19 subgroups!"))))

    (testing "delete group that have children is not allowed"
      (-> session-admin
          (request (str p/service-context t/resource-type "/b")
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 409)))

    (testing "delete subgroup without subgroups is allowed"
      (-> session-admin
          (request (str p/service-context t/resource-type "/c")
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]])))
