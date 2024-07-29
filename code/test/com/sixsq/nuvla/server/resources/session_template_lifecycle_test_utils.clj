(ns com.sixsq.nuvla.server.resources.session-template-lifecycle-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [is]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.session-template :as st]))


(defn check-existing-session-template [base-uri valid-template]

  (let [method        (:method valid-template)
        session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

    ;; should be an existing template already
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-template))
        (ltu/body->edn)
        (ltu/is-status 409))

    ;; session template should be visible via query as well
    ;; should have one with the correct method name
    (let [entries (-> session-anon
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-resource-uri st/collection-type)
                      (ltu/entries))]
      (is (= 1 (count (filter #(= method (:method %)) entries)))))

    ;; do full lifecycle for a session template
    (let [uri     (str st/resource-type "/" method)
          abs-uri (str p/service-context uri)]

      ;; delete the template
      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the template is gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404))

      ;; session template should not be there anymore
      (ltu/refresh-es-indices)
      (let [entries (-> session-anon
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri st/collection-type)
                        (ltu/entries))]
        (is (zero? (count (filter #(= method (:method %)) entries))))))))

(defn session-template-lifecycle [base-uri valid-template]

  (let [method        (:method valid-template)
        session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

    ;; all view actions should be available to anonymous users
    ;; count may not be zero because of session template initialization
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri st/collection-type)
        (ltu/is-operation-absent :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; for admin, should be able to add as well
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri st/collection-type)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; creating with an unknown authentication method should fail
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-template :method "UNKNOWN")))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; do full lifecycle for a session template
    (let [uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-template))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; ensure that the created template can be retrieved by anyone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent :add)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-present :edit))

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit))

      ;; verify that the id corresponds to the value in the instance parameter
      (let [{:keys [id instance]} (-> session-anon
                                      (request abs-uri)
                                      (ltu/body->edn)
                                      (ltu/body))]
        (is (= id (str st/resource-type "/" instance))))

      ;; verify that editing/updating the template works
      (let [orig-template    (-> session-anon
                                 (request abs-uri)
                                 (ltu/body->edn)
                                 (ltu/body))
            updated-template (assoc orig-template :name "UPDATED_NAME")]

        (-> session-admin
            (request abs-uri
                     :request-method :put
                     :body (json/write-str updated-template))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [reread-template (-> session-anon
                                  (request abs-uri)
                                  (ltu/body->edn)
                                  (ltu/body))]

          (is (= (dissoc orig-template :name :updated)
                 (dissoc reread-template :name :updated :updated-by)))
          (is (= "UPDATED_NAME" (:name reread-template)))
          (is (not= (:name orig-template) (:name reread-template)))
          (is (not= (:updated orig-template) (:updated reread-template)))))

      ;; session template should be visible via query as well
      (let [entries (-> session-anon
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri st/collection-type)
                        (ltu/entries))]
        (is (= 1 (count (filter #(= method (:method %)) entries)))))

      ;; delete the template
      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the template is gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404))

      ;; session template should not be there anymore
      (ltu/refresh-es-indices)
      (let [entries (-> session-anon
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri st/collection-type)
                        (ltu/entries))]
        (is (zero? (count (filter #(= method (:method %)) entries))))))))

(defn bad-methods [base-uri]
  (let [resource-uri (str p/service-context (u/new-resource-id st/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
