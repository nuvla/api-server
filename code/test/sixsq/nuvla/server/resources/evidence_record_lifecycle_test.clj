(ns sixsq.nuvla.server.resources.evidence-record-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [sixsq.nuvla.server.resources.evidence-record :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


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
   :start-time                        "1964-08-25T10:00:00.00Z"
   :end-time                          "1964-08-25T10:00:00.00Z"
   :class                             "className"
   (keyword (str ns1-prefix ":att1")) "123.456"})


(def valid-nested-2-levels
  {:passed                            true
   :plan-id                           "abcd"
   :start-time                        "1964-08-25T10:00:00.00Z"
   :end-time                          "1964-08-25T10:00:00.00Z"
   :class                             "className"
   (keyword (str ns1-prefix ":att1")) {(keyword (str ns1-prefix ":att2")) "456"}})


(def valid-nested-entry
  {:passed                                 true
   :plan-id                                "abcd"
   :start-time                             "1964-08-25T10:00:00.00Z"
   :end-time                               "1964-08-25T10:00:00.00Z"
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
   :start-time                            "1964-08-25T10:00:00.00Z"
   :end-time                              "1964-08-25T10:00:00.00Z"
   :class                                 "className"
   (keyword (str invalid-prefix ":att1")) "123.456"})


(defn create-service-attribute-namespaces-fixture
  [f]
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))]

    (doseq [namespace [ns1 ns2]]
      (-> session-admin
          (request (str p/service-context sn/resource-type)
                   :request-method :post
                   :body (json/write-str namespace))
          (ltu/body->edn)
          (ltu/is-status 201))))
  (f))


(use-fixtures :once (join-fixtures [ltu/with-test-server-fixture create-service-attribute-namespaces-fixture]))


(deftest lifecycle

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))
        session-user  (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon"))
        session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))]

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anonymous query should also fail
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; creation rejected because attribute belongs to unknown namespace
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str entry-wrong-namespace))
        (ltu/is-status 406))

    ;; both admin and user should be able to add, query, and delete entries
    (doseq [session [session-user session-admin]]
      (let [uri     (-> session
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str valid-entry))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))
            abs-uri (str p/service-context uri)]

        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session
            (request abs-uri
                     :request-method :put
                     :body (json/write-str valid-entry))
            (ltu/body->edn)
            (ltu/is-status 405))

        (-> session
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; try adding invalid entry
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-entry))
            (ltu/body->edn)
            (ltu/is-status 400))))

    ;; add a new entry
    (let [uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      (is uri)

      ;; verify that the new entry is accessible
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (dissoc :acl)                                     ;; ACL added automatically
          (ltu/does-body-contain valid-entry))

      ;; query to see that entry is listed
      (let [entries (-> session-admin
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri t/collection-type)
                        (ltu/is-count pos?)
                        (ltu/entries))]

        (is ((set (map :id entries)) uri))

        ;; delete the entry
        (-> session-admin
            (request abs-uri :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; ensure that it really is gone
        (-> session-admin
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 404))))))


(deftest uris-as-keys

  (let [session-admin       (-> (session (ltu/ring-app))
                                (content-type "application/json")
                                (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))
        session-user        (-> (session (ltu/ring-app))
                                (content-type "application/json")
                                (header authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon"))

        with-namespaced-key (format "
    {\"plan-id\":\"abcd\",
     \"passed\": true,
     \"end-time\": \"1964-08-25T10:00:00.00Z\",
     \"start-time\": \"1964-08-25T10:00:00.00Z\",
     \"class\": \"className\",
     \"%s:attr-name\":\"123.456\"}
     " ns1-prefix)

        uri-of-posted       (-> session-user
                                (request base-uri
                                         :request-method :post
                                         :body with-namespaced-key)
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location))

        abs-uri             (str p/service-context uri-of-posted)

        doc                 (-> session-admin
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/body))]

    (is ((keyword (str ns1-prefix ":attr-name")) doc))
    (is (= "123.456" ((keyword (str ns1-prefix ":attr-name")) doc)))))


(deftest nested-values

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))
        session-user  (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon"))]

    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-nested-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)

          doc     (-> session-admin
                      (request abs-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/body))]

      (is (= "enough of nested" (get-in doc [(keyword (str ns1-prefix ":attnested"))
                                             (keyword (str ns2-prefix ":subnested"))
                                             (keyword (str ns2-prefix ":subsubnested"))
                                             (keyword (str ns1-prefix ":subsubsubnested"))]))))

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-nested-entry))
        (ltu/body->edn)
        (ltu/is-status 406))))


(deftest cimi-filter-namespaced-attributes

  (let [attr          (ltu/random-string)
        valid-entry   {:passed                             true
                       :plan-id                            "abcd"
                       :start-time                         "1964-08-25T10:00:00.00Z"
                       :end-time                           "1964-08-25T10:00:00.00Z"
                       :class                              "className"
                       (keyword (str ns1-prefix ":" attr)) "123.456"}
        session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))]


    ;; create resource for testing queries
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))

    (let [cimi-url-ok        (str p/service-context
                                  t/resource-type
                                  (format "?filter=%s:%s='123.456'" ns1-prefix attr))

          cimi-url-no-result (str p/service-context
                                  t/resource-type
                                  (format "?filter=%s:%s='xxx'" ns1-prefix attr))

          res-all            (-> session-admin
                                 (request (str p/service-context t/resource-type))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          res-ok             (-> session-admin
                                 (request cimi-url-ok)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          res-empty          (-> session-admin
                                 (request cimi-url-no-result)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))]

      (is (pos? (:count res-all)))
      (is (= 1 (:count res-ok)))
      (is (zero? (:count res-empty))))))


(deftest cimi-filter-nested-values

  (let [session-anon          (-> (ltu/ring-app)
                                  session
                                  (content-type "application/json"))
        session-admin-form    (-> (ltu/ring-app)
                                  session
                                  (content-type "application/x-www-form-urlencoded")
                                  (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))
        session-admin-json    (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user          (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        attr1                 (ltu/random-string)
        attr2                 (ltu/random-string)
        valid-nested-2-levels {:passed                              true
                               :plan-id                             "abcd"
                               :start-time                          "1964-08-25T10:00:00.00Z"
                               :end-time                            "1964-08-25T10:00:00.00Z"
                               :class                               "className"
                               (keyword (str ns1-prefix ":" attr1)) {(keyword (str ns1-prefix ":" attr2)) "456"}}]

    ;; create resource for testing queries
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nested-2-levels))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))

    ;; check queries that will select the resource
    (let [cimi-url-ok (str p/service-context
                           t/resource-type
                           (format "?filter=%s:%s/%s:%s='456'" ns1-prefix attr1 ns1-prefix attr2))]

      (-> session-admin-json
          (request cimi-url-ok)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1))

      (-> session-admin-json
          (request cimi-url-ok
                   :request-method :put)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1))

      (-> session-admin-form
          (request cimi-url-ok
                   :request-method :put
                   :body (rc/form-encode {:filter (format "%s:att1/%s:att2='456'" ns1-prefix ns1-prefix)}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1)))

    ;; test queries that do not select the resource
    (let [cimi-url-no-result (str p/service-context
                                  t/resource-type
                                  (format "?filter=%s:%s/%s:%s='xxx'" ns1-prefix attr1 ns1-prefix attr2))]

      (-> session-admin-json
          (request cimi-url-no-result)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      (-> session-admin-json
          (request cimi-url-no-result
                   :request-method :put)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      (-> session-admin-form
          (request cimi-url-no-result
                   :request-method :put
                   :body (rc/form-encode {:filter "schema-org:att1/schema-org:att2='xxx'"}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]
                            [resource-uri :put]])))



