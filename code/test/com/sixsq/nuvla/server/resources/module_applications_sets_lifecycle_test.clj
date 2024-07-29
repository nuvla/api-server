(ns com.sixsq.nuvla.server.resources.module-applications-sets-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module-applications-sets :as t]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl {:owners ["group/nuvla-admin"]})


(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-entry {:id                (str t/resource-type "/module-applications-sets-uuid")
                  :resource-type     t/resource-type
                  :created           timestamp
                  :updated           timestamp
                  :acl               valid-acl

                  :author            "someone"
                  :commit            "wip"

                  :applications-sets [{:name         "x"
                                       :applications [{:id      "module/x"
                                                       :version 0}]}]})


(deftest lifecycle

  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header
                              "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    ;; create: NOK for anon, users
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; queries: OK for admin, NOK for others
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 0))

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-entry))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))

          abs-uri (str p/service-context uri)]

      ;; retrieve: OK for admin; NOK for others
      (doseq [session [session-anon session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; delete: OK for admin; NOK for others
      (doseq [session [session-anon session-user]]
        (-> session
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the resource was deleted.
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))

(deftest app-set-files
  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        files         [{:file-name "file1.yaml", :file-content "file1 content"}
                       {:file-name "file2.yaml", :file-content "file2 content"}]
        files-updated [{:file-name "file1.yaml", :file-content "file1 content updated"}
                       {:file-name "file2.yaml", :file-content "file2 content updated"}]
        valid-entry   (assoc-in valid-entry [:applications-sets 0 :applications 0 :files] files)
        updated-entry (assoc-in valid-entry [:applications-sets 0 :applications 0 :files] files-updated)
        uri           (-> session-admin
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str valid-entry))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))
        abs-uri       (str p/service-context uri)]

    (-> session-admin
        (request abs-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value (comp :files first :applications first) :applications-sets files))

    (-> session-admin
        (request abs-uri
                 :request-method :put
                 :body (json/write-str updated-entry))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value (comp :files first :applications first) :applications-sets files-updated))

    (-> session-admin
        (request abs-uri
                 :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; verify that the resource was deleted.
    (-> session-admin
        (request abs-uri)
        (ltu/body->edn)
        (ltu/is-status 404))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
