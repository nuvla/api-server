(ns sixsq.nuvla.server.resources.evidence-record-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.evidence-record :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as tu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]))


(def base-uri (str p/service-context t/resource-type))


(def ns1-prefix (ltu/random-string "ns1-"))


(def ns2-prefix (ltu/random-string "ns2-"))


(def invalid-prefix (ltu/random-string))


(def ns1 {:prefix ns1-prefix
          :uri    (str "https://example.org/" ns1-prefix)})


(def ns2 {:prefix ns2-prefix
          :uri    (str "https://example.org/" ns2-prefix)})


(def valid-entry
  {:passed                            true
   :plan-id                           "abcd"
   :start-time                        "1964-08-25T10:00:00.0Z"
   :end-time                          "1964-08-25T10:00:00.0Z"
   :class                             "className"
   (keyword (str ns1-prefix ":att1")) "123.456"})


(def valid-nested-2-levels
  {:passed                            true
   :plan-id                           "abcd"
   :start-time                        "1964-08-25T10:00:00.0Z"
   :end-time                          "1964-08-25T10:00:00.0Z"
   :class                             "className"
   (keyword (str ns1-prefix ":att1")) {(keyword (str ns1-prefix ":att2")) "456"}})


(def valid-nested-entry
  {:passed                                 true
   :plan-id                                "abcd"
   :start-time                             "1964-08-25T10:00:00.0Z"
   :end-time                               "1964-08-25T10:00:00.0Z"
   :class                                  "className"
   (keyword (str ns1-prefix ":att1"))      "hi"
   (keyword (str ns1-prefix ":attnested")) {(keyword (str ns2-prefix ":subnested"))
                                            {(keyword (str ns2-prefix ":subsubnested"))
                                             {(keyword (str ns1-prefix ":subsubsubnested")) "enough of nested"}}}})


(def invalid-nested-entry
  (assoc-in valid-nested-entry [(keyword (str ns1-prefix ":attnested"))
                                (keyword (str ns2-prefix ":subnested"))
                                (keyword (str ns2-prefix ":subsubnested"))]
            {(keyword (str invalid-prefix ":subsubsubnested")) "so sad"}))


(def invalid-entry
  {:other "BAD"})


; will be used to crosscheck with the existing namespaces (above)
; it will not exist thus should be rejected
; only schema-org and schema-com are valid and existing (see below)
(def entry-wrong-namespace
  {:passed                                true
   :plan-id                               "abcd"
   :start-time                            "1964-08-25T10:00:00.0Z"
   :end-time                              "1964-08-25T10:00:00.0Z"
   :class                                 "className"
   (keyword (str invalid-prefix ":att1")) "123.456"})


(defn create-service-attribute-namespaces-fixture
  [f]
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))]

    (doseq [namespace [ns1 ns2]]
      (-> session-admin
          (request (str p/service-context sn/resource-type)
                   :request-method :post
                   :body (json/write-str namespace))
          (tu/body->edn)
          (tu/is-status 201))))
  (f))


(use-fixtures :once (join-fixtures [tu/with-test-server-fixture create-service-attribute-namespaces-fixture]))


(deftest lifecycle

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))]

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (tu/body->edn)
        (tu/is-status 403))

    ;; anonymous query should also fail
    (-> session-anon
        (request base-uri)
        (tu/body->edn)
        (tu/is-status 403))

    ;; creation rejected because attribute belongs to unknown namespace
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str entry-wrong-namespace))
        (tu/is-status 406))

    ;; both admin and user should be able to add, query, and delete entries
    (doseq [session [session-user session-admin]]
      (let [uri (-> session
                    (request base-uri
                             :request-method :post
                             :body (json/write-str valid-entry))
                    (tu/body->edn)
                    (tu/is-status 201)
                    (tu/location))
            abs-uri (str p/service-context uri)]

        (-> session
            (request abs-uri)
            (tu/body->edn)
            (tu/is-status 200))

        (-> session
            (request abs-uri
                     :request-method :put
                     :body (json/write-str valid-entry))
            (tu/body->edn)
            (tu/is-status 405))

        (-> session
            (request abs-uri
                     :request-method :delete)
            (tu/body->edn)
            (tu/is-status 200))

        ;; try adding invalid entry
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-entry))
            (tu/body->edn)
            (tu/is-status 400))))

    ;; add a new entry
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (tu/body->edn)
                  (tu/is-status 201)
                  (tu/location))
          abs-uri (str p/service-context uri)]

      (is uri)

      ;; verify that the new entry is accessible
      (-> session-admin
          (request abs-uri)
          (tu/body->edn)
          (tu/is-status 200)
          (dissoc :acl)                                     ;; ACL added automatically
          (tu/does-body-contain valid-entry))

      ;; query to see that entry is listed
      (let [entries (-> session-admin
                        (request base-uri)
                        (tu/body->edn)
                        (tu/is-status 200)
                        (tu/is-resource-uri t/collection-type)
                        (tu/is-count pos?)
                        (tu/entries))]

        (is ((set (map :id entries)) uri))

        ;; delete the entry
        (-> session-admin
            (request abs-uri :request-method :delete)
            (tu/body->edn)
            (tu/is-status 200))

        ;; ensure that it really is gone
        (-> session-admin
            (request abs-uri)
            (tu/body->edn)
            (tu/is-status 404))))))


(deftest uris-as-keys

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))]

    (let [with-namespaced-key (format "
    {\"plan-id\":\"abcd\",
     \"passed\": true,
     \"end-time\": \"1964-08-25T10:00:00.0Z\",
     \"start-time\": \"1964-08-25T10:00:00.0Z\",
     \"class\": \"className\",
     \"%s:attr-name\":\"123.456\"}
     " ns1-prefix)

          uri-of-posted (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body with-namespaced-key)
                            (tu/body->edn)
                            (tu/is-status 201)
                            (tu/location))

          abs-uri (str p/service-context uri-of-posted)

          doc (-> session-admin
                  (request abs-uri)
                  (tu/body->edn)
                  (tu/is-status 200)
                  (get-in [:response :body]))]

      (is ((keyword (str ns1-prefix ":attr-name")) doc))
      (is (= "123.456" ((keyword (str ns1-prefix ":attr-name")) doc))))))


(deftest nested-values

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))]

    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-nested-entry))
                  (tu/body->edn)
                  (tu/is-status 201)
                  (tu/location))
          abs-uri (str p/service-context uri)

          doc (-> session-admin
                  (request abs-uri)
                  (tu/body->edn)
                  (tu/is-status 200)
                  (get-in [:response :body]))]

      (is (= "enough of nested" (get-in doc [(keyword (str ns1-prefix ":attnested"))
                                             (keyword (str ns2-prefix ":subnested"))
                                             (keyword (str ns2-prefix ":subsubnested"))
                                             (keyword (str ns1-prefix ":subsubsubnested"))]))))

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-nested-entry))
        (tu/body->edn)
        (tu/is-status 406))))


(deftest cimi-filter-namespaced-attributes

  (let [attr (ltu/random-string)
        valid-entry {:passed                             true
                     :plan-id                            "abcd"
                     :start-time                         "1964-08-25T10:00:00.0Z"
                     :end-time                           "1964-08-25T10:00:00.0Z"
                     :class                              "className"
                     (keyword (str ns1-prefix ":" attr)) "123.456"}
        session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))]


    ;; create resource for testing queries
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (tu/body->edn)
        (tu/is-status 201)
        (tu/location))

    (let [cimi-url-ok (str p/service-context
                           t/resource-type
                           (format "?filter=%s:%s='123.456'" ns1-prefix attr))

          cimi-url-no-result (str p/service-context
                                  t/resource-type
                                  (format "?filter=%s:%s='xxx'" ns1-prefix attr))

          res-all (-> session-admin
                      (request (str p/service-context t/resource-type))
                      (tu/body->edn)
                      (tu/is-status 200)
                      (get-in [:response :body]))

          res-ok (-> session-admin
                     (request cimi-url-ok)
                     (tu/body->edn)
                     (tu/is-status 200)
                     (get-in [:response :body]))

          res-empty (-> session-admin
                        (request cimi-url-no-result)
                        (tu/body->edn)
                        (tu/is-status 200)
                        (get-in [:response :body]))]

      (is (pos? (:count res-all)))
      (is (= 1 (:count res-ok)))
      (is (zero? (:count res-empty))))))


(deftest cimi-filter-nested-values

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin-form (-> (ltu/ring-app)
                               session
                               (content-type "application/x-www-form-urlencoded")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-admin-json (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")

        attr1 (ltu/random-string)
        attr2 (ltu/random-string)
        valid-nested-2-levels {:passed                              true
                               :plan-id                             "abcd"
                               :start-time                          "1964-08-25T10:00:00.0Z"
                               :end-time                            "1964-08-25T10:00:00.0Z"
                               :class                               "className"
                               (keyword (str ns1-prefix ":" attr1)) {(keyword (str ns1-prefix ":" attr2)) "456"}}]

    ;; create resource for testing queries
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nested-2-levels))
        (tu/body->edn)
        (tu/is-status 201)
        (tu/location))

    ;; check queries that will select the resource
    (let [cimi-url-ok (str p/service-context
                           t/resource-type
                           (format "?filter=%s:%s/%s:%s='456'" ns1-prefix attr1 ns1-prefix attr2))

          res-ok (-> session-admin-json
                     (request cimi-url-ok)
                     (tu/body->edn)
                     (tu/is-status 200)
                     (get-in [:response :body :count]))

          res-ok-put (-> session-admin-json
                         (request cimi-url-ok
                                  :request-method :put)
                         (tu/body->edn)
                         (tu/is-status 200)
                         (get-in [:response :body :count]))

          res-ok-put-body (-> session-admin-form
                              (request cimi-url-ok
                                       :request-method :put
                                       :body (rc/form-encode {:filter (format "%s:att1/%s:att2='456'" ns1-prefix ns1-prefix)}))
                              (tu/body->edn)
                              (tu/is-status 200)
                              (get-in [:response :body :count]))]

      (is (= 1 res-ok))
      (is (= 1 res-ok-put))
      (is (= 1 res-ok-put-body)))

    ;; test queries that do not select the resource
    (let [cimi-url-no-result (str p/service-context
                                  t/resource-type
                                  (format "?filter=%s:%s/%s:%s='xxx'" ns1-prefix attr1 ns1-prefix attr2))

          no-result (-> session-admin-json
                        (request cimi-url-no-result)
                        (tu/body->edn)
                        (tu/is-status 200)
                        (get-in [:response :body :count]))

          no-result-put (-> session-admin-json
                            (request cimi-url-no-result
                                     :request-method :put)
                            (tu/body->edn)
                            (tu/is-status 200)
                            (get-in [:response :body :count]))

          no-result-put-body (-> session-admin-form
                                 (request cimi-url-no-result
                                          :request-method :put
                                          :body (rc/form-encode {:filter "schema-org:att1/schema-org:att2='xxx'"}))
                                 (tu/body->edn)
                                 (tu/is-status 200)
                                 (get-in [:response :body :count]))]

      (is (zero? no-result))
      (is (zero? no-result-put))
      (is (zero? no-result-put-body)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]
                            [resource-uri :put]])))



