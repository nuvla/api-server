(ns sixsq.nuvla.server.resources.vendor-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.user-utils-test :as user-utils-test]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.vendor :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture
              (partial user-utils-test/with-existing-user "tarzan@example.com"))


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (if-not (env/env :stripe-api-key)
    (log/error "vendor lifecycle is not tested because lack of stripe-api-key!")
    (let [session-anon  (-> (session (ltu/ring-app))
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header
                                "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session-anon authn-info-header (str @user-utils-test/user-id! " group/nuvla-user group/nuvla-anon"))]


      ;; create: NOK for anon
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; creation should list all required-items
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 303))
      )))



(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))



