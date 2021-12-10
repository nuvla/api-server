(ns sixsq.nuvla.server.resources.data-record-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest testing is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record :as t]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.spec.data-test :as dts]))


(def base-uri (str p/service-context t/resource-type))


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


(def valid-entry-minimal
  {:infrastructure-service  "infrastructure-service/1-2-3-4-5"})


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

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

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

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      (let [data-record-acl (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/body)
                                :acl)]

        ;; check the default ACL
        (is ((set (:owners data-record-acl)) "group/nuvla-admin"))
        (is ((set (:edit-acl data-record-acl)) "user/jane")))

      (-> (session (ltu/ring-app))
          (header authn-info-header "user/jane user/jane role1 group/nuvla-admin")
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    ;; adding as user, retrieving and deleting entry as group/nuvla-admin should work
    (let [uri     (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; try adding invalid entry
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str invalid-entry))
          (ltu/body->edn)
          (ltu/is-status 400)))

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

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        connector-with-namespaced-key
                      (format "
          {\"infrastructure-service\":\"infrastructure-service/cloud-software-solution\",
          \"%s:attr-name\":\"123.456\"}
          " ns1-prefix)

        uri-of-posted (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body connector-with-namespaced-key)
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

        abs-uri       (str p/service-context uri-of-posted)

        doc           (-> session-admin
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body))]

    (is ((keyword (str ns1-prefix ":attr-name")) doc))
    (is (= "123.456" ((keyword (str ns1-prefix ":attr-name")) doc)))))


(deftest nested-values

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

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

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

        attr          (ltu/random-string)
        valid-entry   {:infrastructure-service             "infrastructure-service/cloud-software-solution-1"
                       (keyword (str ns1-prefix ":" attr)) "123.456"}]

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))

    (let [cimi-url-ok        (str base-uri
                                  (format "?filter=%s:%s='123.456'" ns1-prefix attr))
          cimi-url-no-result (str base-uri
                                  (format "?filter=%s:%s='xxx'" ns1-prefix attr))

          res-all            (-> session-admin
                                 (request base-uri)
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
      (is (= 0 (:count res-empty))))))


(deftest cimi-filter-nested-values

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nested-2-levels))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))

    (let [cimi-url-ok        (str base-uri
                                  (format "?filter=%s:att3/%s:att4='456'" ns1-prefix ns1-prefix))
          cimi-url-no-result (str base-uri
                                  (format "?filter=%s:att3/%s:att4='xxx'" ns1-prefix ns1-prefix))
          res-ok             (-> session-admin
                                 (request cimi-url-ok)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          res-ok-put         (-> session-admin
                                 (request cimi-url-ok
                                          :request-method :put)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          res-ok-put-body    (-> (session (ltu/ring-app))
                                 (content-type "application/x-www-form-urlencoded")
                                 (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
                                 (request cimi-url-ok
                                          :request-method :put
                                          :body (rc/form-encode {:filter (format "%s:att3/%s:att4='456'" ns1-prefix ns1-prefix)}))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          no-result          (-> session-admin
                                 (request cimi-url-no-result)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          no-result-put      (-> session-admin
                                 (request cimi-url-no-result
                                          :request-method :put)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))

          no-result-put-body (-> (session (ltu/ring-app))
                                 (content-type "application/x-www-form-urlencoded")
                                 (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
                                 (request cimi-url-no-result
                                          :request-method :put
                                          :body (rc/form-encode {:filter (format "%s:att3/%s:att4='xxx'" ns1-prefix ns1-prefix)}))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body))]

      (is (= 1 (:count res-ok)))
      (is (= 0 (:count no-result)))
      (is (= 1 (:count res-ok-put)))
      (is (= 0 (:count no-result-put)))
      (is (= 1 (:count res-ok-put-body)))
      (is (= 0 (:count no-result-put-body))))))


(deftest bulk-delete

  (let [session-anon      (-> (session (ltu/ring-app))
                              (content-type "application/json"))
        session-admin     (header session-anon authn-info-header
                                  "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user      (header session-anon authn-info-header
                                  "user/jane user/jane group/nuvla-user group/nuvla-anon")
        data-record-fn    (fn [infra-id]
                            {:infrastructure-service (str "infrastructure-service/" infra-id)})
        count-existing-dr (-> session-admin
                              (request base-uri
                                       :request-method :put)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (get-in [:response :body :count]))
        base-uri-filter   (str base-uri "?filter=id!=null")] ;; empty filter isnn't allowed bulk

    ;; cleanup all existing data record if any
    (when (pos? count-existing-dr)
      (-> session-admin
          (request base-uri-filter
                   :request-method :delete
                   :headers {:bulk true})
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :deleted count-existing-dr)))

    ;; user create 10 data-records
    (doseq [infra-id (range 10)]
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str (data-record-fn infra-id)))
          (ltu/body->edn)
          (ltu/is-status 201)))

    ;; user try to delete but forgot the header bulk
    ;; server doesn't allow bulk delete without the special header

    (-> session-user
        (request base-uri
                 :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 400)
        (ltu/is-key-value :message "Bulk request should contain bulk http header."))

    ;; user try to delete but without a cimi filter
    ;; server doesn't allow bulk delete without a filter

    (-> session-user
        (request base-uri
                 :request-method :delete
                 :headers {:bulk true})
        (ltu/body->edn)
        (ltu/is-status 400)
        (ltu/is-key-value :message "Bulk request should contain a non empty cimi filter."))

    ;; user can use filter in bulk delete operation

    (let [query-filter (str base-uri
                            "?filter=infrastructure-service='infrastructure-service/5'")]
      (-> session-user
          (request query-filter
                   :request-method :delete
                   :headers {:bulk true})
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :deleted 1))

      ;; the data record was deleted
      (-> session-user
          (request query-filter
                   :request-method :put)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 0)))

    ;; user delete the 9 left data records
    (-> session-user
        (request base-uri-filter
                 :request-method :delete
                 :headers {:bulk true})
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :deleted 9))

    ;; admin add data record which can be deleted by user
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc (data-record-fn 100)
                                         :acl {:owners ["group/nuvla-admin"]
                                               :delete ["user/jane"]})))
        (ltu/body->edn)
        (ltu/is-status 201))

    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (data-record-fn 101)))
        (ltu/body->edn)
        (ltu/is-status 201))

    ; delete acl is taken into account for the user
    (-> session-user
        (request base-uri-filter
                 :request-method :delete
                 :headers {:bulk true})
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :deleted 1))

    (-> session-admin
        (request base-uri-filter
                 :request-method :delete
                 :headers {:bulk true})
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :deleted 1))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]])))


(deftest location-as-geo-point
  (testing ":location as geo-point"
    (let [session-anon  (-> (session (ltu/ring-app))
                            (content-type "application/json"))
          session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

          valid-locations (for [p dts/valid-points] (assoc valid-entry-minimal :location p))
          invalid-locations (for [p dts/invalid-points] (assoc valid-entry-minimal :location p))]

      (doseq [loc valid-locations]
        (let [uri (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str loc))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
              abs-uri (str p/service-context uri)]
          (-> session-user
              (request abs-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))))

      (doseq [loc invalid-locations]
        (-> session-user
            (request base-uri
                     :request-method :post
                     :body (json/write-str loc))
            (ltu/body->edn)
            (ltu/is-status 400))))))


(deftest geometry-as-geo-shape
  (testing ":geometry as geo-shape"
    (let [session-anon  (-> (session (ltu/ring-app))
                            (content-type "application/json"))
          session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

          valid-polygons (for [shape dts/valid-polygons]
                                     (assoc valid-entry-minimal :geometry {:type "Polygon" :coordinates shape}))
          invalid-polygons (for [shape (concat dts/invalid-polygons dts/valid-points)]
                                       (assoc valid-entry-minimal :geometry {:type "Polygon" :coordinates shape}))
          valid-points (for [shape dts/valid-points]
                                   (assoc valid-entry-minimal :geometry {:type "Point" :coordinates shape}))
          invalid-points (for [shape (concat dts/invalid-points dts/valid-polygons)]
                                     (assoc valid-entry-minimal :geometry {:type "Point" :coordinates shape}))]

      ;; valid shapes
      (doseq [shape (concat valid-polygons valid-points)]
        (let [uri (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str shape))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
              abs-uri (str p/service-context uri)]
          (-> session-user
              (request abs-uri
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))))

      ;; invalid shapes
      (doseq [shape (concat invalid-polygons invalid-points)]
        (-> session-user
            (request base-uri
                     :request-method :post
                     :body (json/write-str shape))
            (ltu/body->edn)
            (ltu/is-status 400))))))
