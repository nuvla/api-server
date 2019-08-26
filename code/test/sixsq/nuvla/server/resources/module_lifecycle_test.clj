(ns sixsq.nuvla.server.resources.module-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context module/resource-type))


(def timestamp "1964-08-25T10:00:00.00Z")

(defn valid-module
  [subtype content]
  {:id                        (str module/resource-type "/connector-uuid")
   :resource-type             module/resource-type
   :created                   timestamp
   :updated                   timestamp
   :parent-path               "a/b"
   :path                      "a/b/c"
   :subtype                   subtype

   :logo-url                  "https://example.org/logo"

   :data-accept-content-types ["application/json" "application/x-something"]
   :data-access-protocols     ["http+s3" "posix+nfs"]

   :content                   content})


(defn lifecycle-test-module
  [subtype valid-content]
  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header
                              "user/jane group/nuvla-user group/nuvla-anon")

        valid-entry   {:id                        (str module/resource-type "/connector-uuid")
                       :resource-type             module/resource-type
                       :created                   timestamp
                       :updated                   timestamp
                       :parent-path               "a/b"
                       :path                      "a/b/c"
                       :subtype                   subtype

                       :logo-url                  "https://example.org/logo"

                       :data-accept-content-types ["application/json" "application/x-something"]
                       :data-access-protocols     ["http+s3" "posix+nfs"]

                       :content                   valid-content}]

    ;; create: NOK for anon
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; queries: NOK for anon
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)))

    ;; invalid module subtype
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-entry :subtype "bad-module-subtype")))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; adding, retrieving and  deleting entry as user should succeed
    (doseq [session [session-admin session-user]]
      (let [uri     (-> session
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str valid-entry))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))

            abs-uri (str p/service-context uri)]

        ;; retrieve: NOK for anon
        (-> session-anon
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403))

        (let [content (-> session-admin
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          :response
                          :body
                          :content)]
          (is (= valid-content (select-keys content (keys valid-content)))))

        ;; edit: NOK for anon
        (-> session-anon
            (request abs-uri
                     :request-method :put
                     :body (json/write-str valid-entry))
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; insert 5 more versions
        (doseq [_ (range 5)]
          (-> session-admin
              (request abs-uri
                       :request-method :put
                       :body (json/write-str valid-entry))
              (ltu/body->edn)
              (ltu/is-status 200)))

        (let [versions (-> session-admin
                           (request abs-uri
                                    :request-method :put
                                    :body (json/write-str valid-entry))
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           :response
                           :body
                           :versions)]
          (is (= 7 (count versions)))

          ;; extract by indexes or last
          (doseq [[i n] [["_0" 0] ["_1" 1] ["" 6]]]
            (let [content-id (-> session-admin
                                 (request (str abs-uri i))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 :response
                                 :body
                                 :content
                                 :id)]
              (is (= (-> versions (nth n) :href) content-id))
              (is (= (-> versions (nth n) :author) "someone"))
              (is (= (-> versions (nth n) :commit) "wip")))))

        (doseq [i ["_0" "_1"]]
          (-> session-admin
              (request (str abs-uri i)
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-admin
              (request (str abs-uri i))
              (ltu/body->edn)
              (ltu/is-status 404)))


        ;; delete out of bound index should return 404
        (-> session-admin
            (request (str abs-uri "_50")
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 404))

        (-> session-admin
            (request (str abs-uri "_50"))
            (ltu/body->edn)
            (ltu/is-status 404))

        ;; delete: NOK for anon
        (-> session-anon
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403))

        (-> session-admin
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the resource was deleted.
        (-> session-admin
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 404))))))

(deftest lifecycle-component
  (let [valid-component {:author        "someone"
                         :commit        "wip"

                         :architectures ["amd64" "arm/v6"]
                         :image         {:image-name "ubuntu"
                                         :tag        "16.04"}
                         :ports         [{:protocol       "tcp"
                                          :target-port    22
                                          :published-port 8022}]}]
    (lifecycle-test-module "component" valid-component)))


(deftest lifecycle-application

  (let [valid-application {:author         "someone"
                           :commit         "wip"

                           :docker-compose "version: \"3.3\"\nservices:\n  web:\n    ..."}]
    (lifecycle-test-module "application" valid-application)))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id module/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
