(ns sixsq.nuvla.server.resources.group-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.group-template :as group-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context group/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists group/resource-type))


(deftest lifecycle

  (let [app                     (ltu/ring-app)
        session-json            (content-type (session app) "application/json")
        session-admin           (header session-json authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user            (header session-json authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
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

    ;; admin query should succeed and have three entries
    (let [entries (-> session-admin
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count 3)
                      (ltu/entries))]
      (is (= #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"} (set (map :id entries))))
      (is (= (every? #(not (nil? %)) (set (map :name entries)))))
      (is (= (every? #(not (nil? %)) (set (map :description entries))))))


    ;; user query should also have three entries, but only common attributes (i.e. no :users field)
    (let [entries (-> session-user
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count 3)
                      (ltu/entries))]
      (is (= #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"} (set (map :id entries))))
      (is (= [nil nil nil] (map :users entries))))

    ;; anon query should see nothing
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; test lifecycle of new group
    (doseq [tpl [valid-create valid-create-no-href]]
      (let [resp        (-> session-admin
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
        (let [{:keys [id name description tags users] :as body} (-> session-admin
                                                                    (request abs-uri)
                                                                    (ltu/body->edn)
                                                                    :response
                                                                    :body)]
          (is (= id expected-id))
          (is (= name name-attr))
          (is (= description description-attr))
          (is (= tags tags-attr))
          (is (= [] users))

          ;; actually add some users to the group
          (let [users ["user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22"
                       "user/bb2f41a3-c54c-fce8-32d2-0324e1c32e22"
                       "user/cc2f41a3-c54c-fce8-32d2-0324e1c32e22"]]

            (-> session-admin
                (request abs-uri
                         :request-method :put
                         :body (json/write-str (assoc body :users users)))
                (ltu/body->edn)
                (ltu/is-status 200))

            (let [{updated-users :users :as body} (-> session-admin
                                                      (request abs-uri)
                                                      (ltu/body->edn)
                                                      :response
                                                      :body)]
              (is (= users updated-users)))))

        ;; delete should work
        (-> session-admin
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id group/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
