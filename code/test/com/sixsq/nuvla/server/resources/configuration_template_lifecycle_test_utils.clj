(ns com.sixsq.nuvla.server.resources.configuration-template-lifecycle-test-utils
  (:require
    [clojure.test :refer [is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-template :as cfg-tpl]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context cfg-tpl/resource-type))


(defn check-retrieve-by-id
  [service]
  (let [id  (str cfg-tpl/resource-type "/" service)
        doc (crud/retrieve-by-id id)]
    (is (= id (:id doc)))))

(defn check-lifecycle
  [service]

  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user")
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")]

    ;; anonymous query is not authorized
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user query is not authorized
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; query as group/nuvla-admin should work correctly
    (let [entries (-> session-admin
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-resource-uri cfg-tpl/collection-type)
                      (ltu/is-count pos?)
                      (ltu/is-operation-absent :add)
                      (ltu/is-operation-absent :delete)
                      (ltu/is-operation-absent :edit)
                      (ltu/entries))
          ids     (set (map :id entries))
          types   (set (map :service entries))]
      (is (contains? ids (str cfg-tpl/resource-type "/" service)))
      (is (contains? types service))

      (doseq [entry entries]
        (let [ops        (ltu/operations->map entry)
              entry-url  (str p/service-context (:id entry))

              entry-resp (-> session-admin
                             (request entry-url)
                             (ltu/is-status 200)
                             (ltu/body->edn))

              entry-body (get-in entry-resp [:response :body])]
          (is (nil? (get ops (name :add))))
          (is (nil? (get ops (name :edit))))
          (is (nil? (get ops (name :delete))))

          (is (crud/validate entry-body))

          ;; anonymous access not permitted
          (-> session-anon
              (request entry-url)
              (ltu/is-status 403))

          ;; user cannot access
          (-> session-user
              (request entry-url)
              (ltu/is-status 403)))))))

(defn check-bad-methods
  []
  (let [resource-uri (str p/service-context (u/new-resource-id cfg-tpl/resource-type))]
    (ltu/verify-405-status [[base-uri :post]
                            [base-uri :delete]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
