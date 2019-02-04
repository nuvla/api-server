(ns com.sixsq.slipstream.ssclj.resources.configuration-template-lifecycle-test-utils
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :refer :all]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase resource-name)))


(defn check-retrieve-by-id
  [service]
  (let [id (str resource-url "/" service)
        doc (crud/retrieve-by-id id)]
    (is (= id (:id doc)))))

(defn check-lifecycle
  [service]

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "jane USER")
        session-admin (header session-anon authn-info-header "root ADMIN")]

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

    ;; query as ADMIN should work correctly
    (let [entries (-> session-admin
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-resource-uri collection-uri)
                      (ltu/is-count pos?)
                      (ltu/is-operation-absent "add")
                      (ltu/is-operation-absent "delete")
                      (ltu/is-operation-absent "edit")
                      (ltu/is-operation-absent "describe")
                      (ltu/entries resource-tag))
          ids (set (map :id entries))
          types (set (map :service entries))]
      (is (contains? ids (str resource-url "/" service)))
      (is (contains? types service))

      (doseq [entry entries]
        (let [ops (ltu/operations->map entry)
              href (get ops (c/action-uri :describe))
              entry-url (str p/service-context (:id entry))
              describe-url (str p/service-context href)

              entry-resp (-> session-admin
                             (request entry-url)
                             (ltu/is-status 200)
                             (ltu/body->edn))

              entry-body (get-in entry-resp [:response :body])

              desc (-> session-admin
                       (request describe-url)
                       (ltu/body->edn)
                       (ltu/is-status 200))
              desc-body (get-in desc [:response :body])]
          (is (nil? (get ops (c/action-uri :add))))
          (is (nil? (get ops (c/action-uri :edit))))
          (is (nil? (get ops (c/action-uri :delete))))
          (is (:service desc-body))
          (is (:acl desc-body))

          (is (crud/validate entry-body))

          ;; anonymous access not permitted
          (-> session-anon
              (request entry-url)
              (ltu/is-status 403))
          (-> session-anon
              (request describe-url)
              (ltu/is-status 403))

          ;; user cannot access
          (-> session-user
              (request entry-url)
              (ltu/is-status 403))
          (-> session-user
              (request describe-url)
              (ltu/is-status 403)))))))

(defn check-bad-methods
  []
  (let [resource-uri (str p/service-context (u/new-resource-id resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :post]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
