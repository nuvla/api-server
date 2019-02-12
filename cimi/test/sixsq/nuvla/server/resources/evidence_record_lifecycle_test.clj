(ns sixsq.nuvla.server.resources.evidence-record-lifecycle-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer :all]
            [peridot.core :refer :all]
            [ring.util.codec :as rc]
            [sixsq.nuvla.server.app.params :as p]
            [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.evidence-record :refer :all]
            [sixsq.nuvla.server.resources.lifecycle-test-utils :as t]
            [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
            [sixsq.nuvla.server.resources.service-attribute-namespace :as sn]))

(use-fixtures :each t/with-test-server-fixture)

(def base-uri (str p/service-context resource-type))

(def valid-entry
  {:passed          true
   :planID          "abcd"
   :startTime       "1964-08-25T10:00:00.0Z"
   :endTime         "1964-08-25T10:00:00.0Z"
   :class           "className"
   :schema-org:att1 "123.456"})

(def valid-nested-2-levels
  {:passed          true
   :planID          "abcd"
   :startTime       "1964-08-25T10:00:00.0Z"
   :endTime         "1964-08-25T10:00:00.0Z"
   :class           "className"
   :schema-org:att1 {:schema-org:att2 "456"}})

(def valid-nested-entry
  {:passed          true
   :planID          "abcd"
   :startTime       "1964-08-25T10:00:00.0Z"
   :endTime         "1964-08-25T10:00:00.0Z"
   :class           "className"
   :schema-org:att1 "hi"
   :schema-org:attnested
                    {:schema-com:subnested
                     {:schema-com:subsubnested
                      {:schema-org:subsubsubnested "enough of nested"}}}})

(def invalid-nested-entry
  (assoc-in valid-nested-entry [:schema-org:attnested
                                :schema-com:subnested
                                :schema-com:subsubnested]
            {:schema-XXX:subsubsubnested "so sad"}))

(def invalid-entry
  {:other "BAD"})

; will be used to crosscheck with the existing namespaces (above)
; it will not exist thus should be rejected
; only schema-org and schema-com are valid and existing (see below)
(def entry-wrong-namespace
  {:passed     true
   :planID     "abcd"
   :startTime  "1964-08-25T10:00:00.0Z"
   :endTime    "1964-08-25T10:00:00.0Z"
   :class      "className"
   :wrong:att1 "123.456"})

(def valid-namespace {:prefix "schema-org"
                      :uri    "https://schema-org/a/b/c.md"})

(def namespace-com {:prefix "schema-com"
                    :uri    "https://avida/dollar"})

(deftest lifecycle

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))]

    ;; create namespace
    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (t/body->edn)
        (t/is-status 403))

    ;; anonymous query should also fail
    (-> session-anon
        (request base-uri)
        (t/body->edn)
        (t/is-status 403))

    ;; creation rejected because attribute belongs to unknown namespace
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str entry-wrong-namespace))
        (t/is-status 406))

    ;; both admin and user should be able to add, query, and delete entries
    (doseq [session [session-user session-admin]]
      (let [uri (-> session
                    (request base-uri
                             :request-method :post
                             :body (json/write-str valid-entry))
                    (t/body->edn)
                    (t/is-status 201)
                    (t/location))
            abs-uri (str p/service-context uri)]

        (-> session
            (request abs-uri)
            (t/body->edn)
            (t/is-status 200))

        (-> session
            (request abs-uri
                     :request-method :put
                     :body (json/write-str valid-entry))
            (t/body->edn)
            (t/is-status 405))

        (-> session
            (request abs-uri
                     :request-method :delete)
            (t/body->edn)
            (t/is-status 200))

        ;; try adding invalid entry
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-entry))
            (t/body->edn)
            (t/is-status 400))))

    ;; add a new entry
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (t/body->edn)
                  (t/is-status 201)
                  (t/location))
          abs-uri (str p/service-context uri)]

      (is uri)

      ;; verify that the new entry is accessible
      (-> session-admin
          (request abs-uri)
          (t/body->edn)
          (t/is-status 200)
          (dissoc :acl)                                     ;; ACL added automatically
          (t/does-body-contain valid-entry))

      ;; query to see that entry is listed
      (let [entries (-> session-admin
                        (request base-uri)
                        (t/body->edn)
                        (t/is-status 200)
                        (t/is-resource-uri collection-type)
                        (t/is-count pos?)
                        (t/entries ))]

        (is ((set (map :id entries)) uri))

        ;; delete the entry
        (-> session-admin
            (request abs-uri :request-method :delete)
            (t/body->edn)
            (t/is-status 200))

        ;; ensure that it really is gone
        (-> session-admin
            (request abs-uri)
            (t/body->edn)
            (t/is-status 404))))))


(deftest uris-as-keys

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))]

    ;; create namespace
    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    (let [with-namespaced-key
          (str "{\"planID\":\"abcd\","
               "\"passed\": true,"
               "\"endTime\": \"1964-08-25T10:00:00.0Z\","
               "\"startTime\": \"1964-08-25T10:00:00.0Z\","
               "\"class\": \"className\","
               "\"schema-org:attr-name\":\"123.456\"}")

          uri-of-posted (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body with-namespaced-key)
                            (t/body->edn)
                            (t/is-status 201)
                            (t/location))

          abs-uri (str p/service-context uri-of-posted)

          doc (-> session-admin
                  (request abs-uri)
                  (t/body->edn)
                  (t/is-status 200)
                  (get-in [:response :body]))]

      (is (:schema-org:attr-name doc))
      (is (= "123.456" (:schema-org:attr-name doc))))))


(deftest nested-values

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))]

    ;; create namespaces
    (doseq [namespace [valid-namespace namespace-com]]
      (-> session-admin
          (request (str p/service-context sn/resource-type)
                   :request-method :post
                   :body (json/write-str namespace))
          (t/body->edn)
          (t/is-status 201)))

    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-nested-entry))
                  (t/body->edn)
                  (t/is-status 201)
                  (t/location))
          abs-uri (str p/service-context uri)

          doc (-> session-admin
                  (request abs-uri)
                  (t/body->edn)
                  (t/is-status 200)
                  (get-in [:response :body]))]

      (is (= "enough of nested" (get-in doc [:schema-org:attnested
                                             :schema-com:subnested
                                             :schema-com:subsubnested
                                             :schema-org:subsubsubnested]))))

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-nested-entry))
        (t/body->edn)
        (t/is-status 406))))


(deftest cimi-filter-namespaced-attributes

  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "super ADMIN USER ANON"))]


    ;; create namespaces
    (doseq [namespace [valid-namespace namespace-com]]
      (-> session-admin
          (request (str p/service-context sn/resource-type)
                   :request-method :post
                   :body (json/write-str namespace))
          (t/body->edn)
          (t/is-status 201)))

    ;; create resource for testing queries
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (t/body->edn)
        (t/is-status 201)
        (t/location))

    (let [cimi-url-ok (str p/service-context
                           resource-type
                           "?filter=schema-org:att1='123.456'")

          cimi-url-no-result (str p/service-context
                                  resource-type
                                  "?filter=schema-org:att1='xxx'")

          res-all (-> session-admin
                      (request (str p/service-context resource-type))
                      (t/body->edn)
                      (t/is-status 200)
                      (get-in [:response :body]))

          res-ok (-> session-admin
                     (request cimi-url-ok)
                     (t/body->edn)
                     (t/is-status 200)
                     (get-in [:response :body]))

          res-empty (-> session-admin
                        (request cimi-url-no-result)
                        (t/body->edn)
                        (t/is-status 200)
                        (get-in [:response :body]))]

      (is (= 1 (:count res-all)))
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
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; create namespaces
    (doseq [namespace [valid-namespace namespace-com]]
      (-> session-admin-json
          (request (str p/service-context sn/resource-type)
                   :request-method :post
                   :body (json/write-str namespace))
          (t/body->edn)
          (t/is-status 201)))

    ;; create resource for testing queries
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nested-2-levels))
        (t/body->edn)
        (t/is-status 201)
        (t/location))

    ;; check queries that will select the resource
    (let [cimi-url-ok (str p/service-context
                           resource-type
                           "?filter=schema-org:att1/schema-org:att2='456'")

          res-ok (-> session-admin-json
                     (request cimi-url-ok)
                     (t/body->edn)
                     (t/is-status 200)
                     (get-in [:response :body :count]))

          res-ok-put (-> session-admin-json
                         (request cimi-url-ok
                                  :request-method :put)
                         (t/body->edn)
                         (t/is-status 200)
                         (get-in [:response :body :count]))

          res-ok-put-body (-> session-admin-form
                              (request cimi-url-ok
                                       :request-method :put
                                       :body (rc/form-encode {:filter "schema-org:att1/schema-org:att2='456'"}))
                              (t/body->edn)
                              (t/is-status 200)
                              (get-in [:response :body :count]))]

      (is (= 1 res-ok))
      (is (= 1 res-ok-put))
      (is (= 1 res-ok-put-body)))

    ;; test queries that do not select the resource
    (let [cimi-url-no-result (str p/service-context
                                  resource-type
                                  "?filter=schema-org:att1/schema-org:att2='xxx'")

          no-result (-> session-admin-json
                        (request cimi-url-no-result)
                        (t/body->edn)
                        (t/is-status 200)
                        (get-in [:response :body :count]))

          no-result-put (-> session-admin-json
                            (request cimi-url-no-result
                                     :request-method :put)
                            (t/body->edn)
                            (t/is-status 200)
                            (get-in [:response :body :count]))

          no-result-put-body (-> session-admin-form
                                 (request cimi-url-no-result
                                          :request-method :put
                                          :body (rc/form-encode {:filter "schema-org:att1/schema-org:att2='xxx'"}))
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body :count]))]

      (is (zero? no-result))
      (is (zero? no-result-put))
      (is (zero? no-result-put-body)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]
                            [resource-uri :put]])))



