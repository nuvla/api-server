(ns sixsq.nuvla.server.resources.data-record-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record :refer :all]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(def base-uri (str p/service-context resource-type))


(def ns1-prefix (ltu/random-string "ns1-"))


(def ns2-prefix (ltu/random-string "ns2-"))


(def invalid-prefix (ltu/random-string))


(def core-attrs {:content-type "text/html; charset=utf-8"
                 :bytes        10234
                 :md5sum       "abcde"
                 :timestamp    "2019-04-15T12:23:53.00Z"
                 :location     [6.143158 46.204391 373.0]
                 :mount        {:mount-type     "volume"
                                :target         "/mnt/bucket"
                                :volume-options {:o      "addr=127.0.0.1"
                                                 :type   "nfs"
                                                 :device ":/data/bucket"}}})


(def ns1 {:prefix ns1-prefix
          :uri    (str "https://example.org/" ns1-prefix)})


(def ns2 {:prefix ns2-prefix
          :uri    (str "https://example.org/" ns2-prefix)})


(def valid-entry
  (merge core-attrs
         {:infrastructure-service            "infrastructure-service/cloud-software-solution-1"
          (keyword (str ns1-prefix ":att1")) "123.456"}))


(def valid-nested-2-levels
  (merge core-attrs
         {:infrastructure-service            "infrastructure-service/cloud-software-solution-2"
          (keyword (str ns1-prefix ":att3")) {(keyword (str ns1-prefix ":att4")) "456"}}))


(def valid-nested-entry
  (merge core-attrs
         {:infrastructure-service                 "infrastructure-service/cloud-software-solution-3"
          (keyword (str ns1-prefix ":att1"))      "hi"
          (keyword (str ns1-prefix ":attnested")) {(keyword (str ns2-prefix ":subnested"))
                                                   {(keyword (str ns2-prefix ":subsubnested"))
                                                    {(keyword (str ns1-prefix ":subsubsubnested")) "enough of nested"}}}}))


(def invalid-nested-entry
  (assoc-in valid-nested-entry [(keyword (str ns1-prefix ":attnested"))
                                (keyword (str ns2-prefix ":subnested"))
                                (keyword (str ns2-prefix ":subsubnested"))]
            {(keyword (str invalid-prefix ":subsubsubnested")) "so sad"}))


(def invalid-entry
  {:other "BAD"})


(def entry-wrong-namespace
  {:infrastructure-service                "infrastructure-service/cloud-software-solution"
   (keyword (str invalid-prefix ":att1")) "123.456"})


(defn create-service-attribute-namespaces-fixture
  [f]
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon"))]

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

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

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
    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (t/body->edn)
                      (t/is-status 201)
                      (t/location))
          abs-uri (str p/service-context uri)]

      (let [data-record-acl (-> session-user
                                (request abs-uri)
                                (t/body->edn)
                                (t/is-status 200)
                                :response
                                :body
                                :acl)]

        ;; check the default ACL
        (is ((set (:owners data-record-acl)) "group/nuvla-admin"))
        (is ((set (:edit-acl data-record-acl)) "user/jane")))

      (-> (session (ltu/ring-app))
          (header authn-info-header "user/jane role1 group/nuvla-admin")
          (request abs-uri :request-method :delete)
          (t/body->edn)
          (t/is-status 200)))

    ;; adding as user, retrieving and deleting entry as group/nuvla-admin should work
    (let [uri     (-> session-user
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
    (let [uri     (-> session-admin
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


(deftest uris-as-keys

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        connector-with-namespaced-key
                      (format "
          {\"infrastructure-service\":\"infrastructure-service/cloud-software-solution\",
          \"%s:attr-name\":\"123.456\"}
          " ns1-prefix)

        uri-of-posted (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body connector-with-namespaced-key)
                          (t/body->edn)
                          (t/is-status 201)
                          (t/location))

        abs-uri       (str p/service-context uri-of-posted)

        doc           (-> session-admin
                          (request abs-uri)
                          (t/body->edn)
                          (t/is-status 200)
                          (get-in [:response :body]))]

    (is ((keyword (str ns1-prefix ":attr-name")) doc))
    (is (= "123.456" ((keyword (str ns1-prefix ":attr-name")) doc)))))


(deftest nested-values

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-nested-entry))
                      (t/body->edn)
                      (t/is-status 201)
                      (t/location))
          abs-uri (str p/service-context uri)

          doc     (-> session-admin
                      (request abs-uri)
                      (t/body->edn)
                      (t/is-status 200)
                      (get-in [:response :body]))]

      (is (= "enough of nested" (get-in doc [(keyword (str ns1-prefix ":attnested"))
                                             (keyword (str ns2-prefix ":subnested"))
                                             (keyword (str ns2-prefix ":subsubnested"))
                                             (keyword (str ns1-prefix ":subsubsubnested"))]))))

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-nested-entry))
        (t/body->edn)
        (t/is-status 406))))


(deftest cimi-filter-namespaced-attributes

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        attr          (ltu/random-string)
        valid-entry   {:infrastructure-service             "infrastructure-service/cloud-software-solution-1"
                       (keyword (str ns1-prefix ":" attr)) "123.456"}]

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (t/body->edn)
        (t/is-status 201)
        (t/location))

    (let [cimi-url-ok        (str p/service-context
                                  resource-type
                                  (format "?filter=%s:%s='123.456'" ns1-prefix attr))
          cimi-url-no-result (str p/service-context
                                  resource-type
                                  (format "?filter=%s:%s='xxx'" ns1-prefix attr))

          res-all            (-> session-admin
                                 (request (str p/service-context resource-type))
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          res-ok             (-> session-admin
                                 (request cimi-url-ok)
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          res-empty          (-> session-admin
                                 (request cimi-url-no-result)
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))]

      (is (pos? (:count res-all)))
      (is (= 1 (:count res-ok)))
      (is (= 0 (:count res-empty))))))


(deftest cimi-filter-nested-values

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nested-2-levels))
        (t/body->edn)
        (t/is-status 201)
        (t/location))

    (let [cimi-url-ok        (str p/service-context
                                  resource-type
                                  (format "?filter=%s:att3/%s:att4='456'" ns1-prefix ns1-prefix))
          cimi-url-no-result (str p/service-context
                                  resource-type
                                  (format "?filter=%s:att3/%s:att4='xxx'" ns1-prefix ns1-prefix))
          res-ok             (-> session-admin
                                 (request cimi-url-ok)
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          res-ok-put         (-> session-admin
                                 (request cimi-url-ok
                                          :request-method :put)
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          res-ok-put-body    (-> (session (ltu/ring-app))
                                 (content-type "application/x-www-form-urlencoded")
                                 (header authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
                                 (request cimi-url-ok
                                          :request-method :put
                                          :body (rc/form-encode {:filter (format "%s:att3/%s:att4='456'" ns1-prefix ns1-prefix)}))
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          no-result          (-> session-admin
                                 (request cimi-url-no-result)
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          no-result-put      (-> session-admin
                                 (request cimi-url-no-result
                                          :request-method :put)
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))

          no-result-put-body (-> (session (ltu/ring-app))
                                 (content-type "application/x-www-form-urlencoded")
                                 (header authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
                                 (request cimi-url-no-result
                                          :request-method :put
                                          :body (rc/form-encode {:filter (format "%s:att3/%s:att4='xxx'" ns1-prefix ns1-prefix)}))
                                 (t/body->edn)
                                 (t/is-status 200)
                                 (get-in [:response :body]))]

      (is (= 1 (:count res-ok)))
      (is (= 0 (:count no-result)))
      (is (= 1 (:count res-ok-put)))
      (is (= 0 (:count no-result-put)))
      (is (= 1 (:count res-ok-put-body)))
      (is (= 0 (:count no-result-put-body))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
