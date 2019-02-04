(ns com.sixsq.slipstream.ssclj.resources.connector-template-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.connector-template :refer :all]
    [com.sixsq.slipstream.ssclj.resources.connector-template-alpha-example :as example]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase resource-name)))

(deftest check-retrieve-by-id
  (let [id (str resource-url "/" example/cloud-service-type)
        doc (crud/retrieve-by-id id)]
    (is (= id (:id doc)))))

(deftest lifecycle

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

    ;; user can see template
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

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
          types (set (map :cloudServiceType entries))]
      (is (= #{(str resource-url "/" example/cloud-service-type)} ids))
      (is (= #{example/cloud-service-type} types))

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
          (is (:cloudServiceType desc-body))
          (is (:acl desc-body))

          (is (thrown-with-msg? ExceptionInfo #".*resource does not satisfy defined schema.*" (crud/validate entry-body)))
          (is (crud/validate (assoc entry-body :instanceName "alpha-omega")))

          ;; anonymous access not permitted
          (-> session-anon
              (request entry-url)
              (ltu/is-status 403))
          (-> session-anon
              (request describe-url)
              (ltu/is-status 403))

          ;; user can access
          (-> session-user
              (request entry-url)
              (ltu/is-status 200))
          (-> session-user
              (request describe-url)
              (ltu/is-status 200)))))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :post]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
