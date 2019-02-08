(ns sixsq.nuvla.server.resources.service-offer-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.service-attribute-namespace :as sn]
    [sixsq.nuvla.server.resources.service-offer :refer :all]
    [peridot.core :refer :all]
    [ring.util.codec :as rc]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context resource-type))

(def valid-entry
  {:connector       {:href "cloud-software-solution-1"}
   :schema-org:att1 "123.456"})

(def valid-nested-2-levels
  {:connector       {:href "cloud-software-solution-2"}
   :schema-org:att1 {:schema-org:att2 "456"}})

(def valid-nested-entry
  {:connector       {:href "cloud-software-solution-3"}
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

(def entry-wrong-namespace
  {:connector  {:href "cloud-software-solution"}
   :wrong:att1 "123.456"})

(def valid-namespace {:prefix "schema-org"
                      :uri    "https://schema-org/a/b/c.md"})

(def namespace-com {:prefix "schema-com"
                    :uri    "https://avida/dollar"})

(deftest lifecycle

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

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

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (t/body->edn)
                  (t/is-status 201)
                  (t/location))
          abs-uri (str p/service-context uri)]

      (-> session-user
          (request abs-uri)
          (t/body->edn)
          (t/is-status 200))

      (-> (session (ltu/ring-app))
          (header authn-info-header "jane role1 ADMIN")
          (request abs-uri :request-method :delete)
          (t/body->edn)
          (t/is-status 200)))

    ;; adding as user, retrieving and deleting entry as ADMIN should work
    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (t/body->edn)
                  (t/is-status 201)
                  (t/location))
          abs-uri (str p/service-context uri)]

      (-> session-admin
          (request abs-uri)
          (t/body->edn)
          (t/is-status 200))

      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (t/body->edn)
          (t/is-status 200))

      ;; try adding invalid entry
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str invalid-entry))
          (t/body->edn)
          (t/is-status 400)))

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
                        (t/is-resource-uri collection-uri)
                        (t/is-count pos?)
                        (t/entries))]

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


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id resource-type))]
    (doall
      (for [[uri method] [[base-uri :options]
                          [base-uri :delete]
                          [resource-uri :options]
                          [resource-uri :post]]]
        (do
          (-> (session (ltu/ring-app))
              (request uri
                       :request-method method
                       :body (json/write-str {:dummy "value"}))
              (t/is-status 405)))))))


(deftest uris-as-keys

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; create namespace
    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    (let [connector-with-namespaced-key
          (str "{\"connector\":{\"href\":\"cloud-software-solution\"},"
               "\"schema-org:attr-name\":\"123.456\"}")

          uri-of-posted (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body connector-with-namespaced-key)
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

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; create namespaces
    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str namespace-com))
        (t/body->edn)
        (t/is-status 201))

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

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]


    ;; create namespaces
    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    (-> session-user
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
      (is (= 0 (:count res-empty))))))

(deftest cimi-filter-nested-values

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; create namespaces
    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str valid-namespace))
        (t/body->edn)
        (t/is-status 201))

    (-> session-admin
        (request (str p/service-context sn/resource-type)
                 :request-method :post
                 :body (json/write-str namespace-com))
        (t/body->edn)
        (t/is-status 201))

    (let [_ (-> session-user
                (request base-uri
                         :request-method :post
                         :body (json/write-str valid-nested-2-levels))
                (t/body->edn)
                (t/is-status 201)
                (t/location))
          cimi-url-ok (str p/service-context
                           resource-type
                           "?filter=schema-org:att1/schema-org:att2='456'")
          cimi-url-no-result (str p/service-context
                                  resource-type
                                  "?filter=schema-org:att1/schema-org:att2='xxx'")
          res-ok (-> session-admin
                     (request cimi-url-ok)
                     (t/body->edn)
                     (t/is-status 200)
                     (get-in [:response :body]))

          res-ok-put (-> session-admin
                         (request cimi-url-ok
                                  :request-method :put)
                         (t/body->edn)
                         (t/is-status 200)
                         (get-in [:response :body]))

          res-ok-put-body (-> (session (ltu/ring-app))
                              (content-type "application/x-www-form-urlencoded")
                              (header authn-info-header "super ADMIN USER ANON")
                              (request cimi-url-ok
                                       :request-method :put
                                       :body (rc/form-encode {:filter "schema-org:att1/schema-org:att2='456'"}))
                              (t/body->edn)
                              (t/is-status 200)
                              (get-in [:response :body]))

          no-result (-> session-admin
                        (request cimi-url-no-result)
                        (t/body->edn)
                        (t/is-status 200)
                        (get-in [:response :body]))

          no-result-put (-> session-admin
                            (request cimi-url-no-result
                                     :request-method :put)
                            (t/body->edn)
                            (t/is-status 200)
                            (get-in [:response :body]))

          no-result-put-body (-> (session (ltu/ring-app))
                                 (content-type "application/x-www-form-urlencoded")
                                 (header authn-info-header "super ADMIN USER ANON")
                                 (request cimi-url-no-result
                                          :request-method :put
                                          :body (rc/form-encode {:filter "schema-org:att1/schema-org:att2='xxx'"}))
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))]
      (is (= 1 (:count res-ok)))
      (is (= 0 (:count no-result)))
      (is (= 1 (:count res-ok-put)))
      (is (= 0 (:count no-result-put)))
      (is (= 1 (:count res-ok-put-body)))
      (is (= 0 (:count no-result-put-body))))))
