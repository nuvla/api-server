(ns sixsq.nuvla.server.resources.group-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.group-template :as group-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context group-tpl/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists group-tpl/resource-type (str group-tpl/resource-type "-create")))


(deftest lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-jane  (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown group/nuvla-anon")]

    ;; admin user collection query should succeed and contain exactly 1 template
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 1)
        (ltu/is-operation-absent :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; a query for anyone else should fail
    (doseq [session [session-jane session-anon]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))


    (let [group-tpl-id (str group-tpl/resource-type "/generic")
          abs-url      (str p/service-context group-tpl-id)]
      (-> session-admin
          (request abs-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id group-tpl/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [base-uri :post]
                            [resource-uri :options]
                            [resource-uri :post]])))
