(ns sixsq.nuvla.server.resources.group-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.user-utils-test :as user-utils-test]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.group :as t]
    [sixsq.nuvla.server.resources.group-template :as group-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(def tarzan-email "tarzan@example.com")

(use-fixtures :once ltu/with-test-server-fixture)

(use-fixtures :once ltu/with-test-server-fixture
              (partial user-utils-test/with-existing-user tarzan-email))


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle

  (let [app                     (ltu/ring-app)
        session-json            (content-type (session app) "application/json")
        session-admin           (header session-json authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
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
                                 :template    {:group-identifier valid-create-no-href-id}}]

    ;; admin query should succeed and have 5 entries
    (let [entries (-> session-admin
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count 5)
                      (ltu/entries))]
      (is (= #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-nuvlabox"
               "group/nuvla-anon" "group/nuvla-vpn"} (set (map :id entries))))
      (is (every? #(not (nil? %)) (set (map :name entries))))
      (is (every? #(not (nil? %)) (set (map :description entries)))))


    ;; user query should also have 5 entries, but only common attributes (i.e. no :users field)
    (let [entries (-> session-user
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count 5)
                      (ltu/entries))]
      (is (= #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-nuvlabox"
               "group/nuvla-anon" "group/nuvla-vpn"} (set (map :id entries))))
      (is (= [nil nil nil nil nil] (map :users entries))))

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
          (let [resp        (-> session
                                (request base-uri
                                         :request-method :post
                                         :body (json/write-str tpl))
                                (ltu/body->edn)
                                (ltu/is-status 201))

                abs-uri     (->> resp
                                 ltu/location
                                 (str p/service-context))

                expected-id (str "group/" (get-in tpl [:template :group-identifier]))]

            ;; check contents of resource
            (let [{:keys [id name description tags users]
                   :as   body} (-> session
                                   (request abs-uri)
                                   (ltu/body->edn)
                                   (ltu/body))]
              (is (= id expected-id))
              (is (= name name-attr))
              (is (= description description-attr))
              (is (= tags tags-attr))
              (is (= [] users))

              ;; actually add some users to the group
              (let [users      [@user-utils-test/user-id!
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
                       (ltu/message-matches "server configuration do not authorize following rediect-url:")))

                  (is (= users updated-users))
                  (is (= (set (conj users id)) (set (remove #{"group/nuvla-admin" "group/nuvla-vpn"} (:view-meta acl))))))))

            ;; delete should work
            (-> session
                (request abs-uri
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200))))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]])))
