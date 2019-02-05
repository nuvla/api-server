(ns com.sixsq.slipstream.ssclj.resources.session-template-lifecycle-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.session-template :refer :all]
    [peridot.core :refer :all]))

(defn session-template-lifecycle [base-uri valid-template]

  (let [method (:method valid-template)
        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")]

    ;; all view actions should be available to anonymous users
    ;; count may not be zero because of session template initialization
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri collection-uri)
        (ltu/is-operation-absent "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; for admin, should be able to add as well
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri collection-uri)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; creating with an unknown authentication method should fail
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-template :method "UNKNOWN")))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; do full lifecycle for an internal session template
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-template))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))]

      ;; ensure that the created template can be retrieved by anyone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent "add")
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit"))

      ;; verify that the id corresponds to the value in the instance parameter
      (let [{:keys [id instance]} (-> session-anon
                                      (request abs-uri)
                                      (ltu/body->edn)
                                      :response
                                      :body)]
        (is (= id (str resource-url "/" instance))))

      ;; verify that editing/updating the template works
      (let [orig-template (-> session-anon
                              (request abs-uri)
                              (ltu/body->edn)
                              :response
                              :body)
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
                                  :response
                                  :body)]

          (is (= (dissoc orig-template :name :updated) (dissoc reread-template :name :updated)))
          (is (= "UPDATED_NAME" (:name reread-template)))
          (is (not= (:name orig-template) (:name reread-template)))
          (is (not= (:updated orig-template) (:updated reread-template)))))

      ;; session template should be visible via query as well
      (let [entries (-> session-anon
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri collection-uri)
                        (ltu/entries :sessionTemplates))]
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
                        (ltu/is-resource-uri collection-uri)
                        (ltu/entries :sessionTemplates))]
        (is (zero? (count (filter #(= method (:method %)) entries))))))))

(defn bad-methods [base-uri]
  (let [resource-uri (str p/service-context (u/new-resource-id resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
