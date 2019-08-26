(ns sixsq.nuvla.server.resources.infrastructure-service-template-test
  (:require
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-generic :as tpl-generic]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context tpl/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists "infrastructure-service-template-generic"))


(deftest ensure-templates-exist
  (doseq [subtype [tpl-generic/method]]

    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown group/nuvla-anon")

          tpl           (str tpl/resource-type "/" subtype)
          resource-uri  (str p/service-context tpl)]

      ;; anonymous access to template must fail
      (-> session-anon
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; admin and user access must succeed
      (doseq [session [session-admin session-user]]
        (-> session
            (request resource-uri)
            (ltu/body->edn)
            (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context tpl/resource-type "/" tpl-generic/method)]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :post]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
