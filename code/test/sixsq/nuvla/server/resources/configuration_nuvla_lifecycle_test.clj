(ns sixsq.nuvla.server.resources.configuration-nuvla-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.configuration :as cfg]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as ct-nuvla]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context cfg/resource-type))


;; must have specialized checks for the nuvla configuration because
;; the initialization function creates a default nuvla configuration
;; resource

(defn check-existing-configuration
  [service attr-kw attr-value]

  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        template-url  (str p/service-context ct/resource-type "/" service)
        template      (-> session-admin
                          (request template-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body))

        valid-create  {:template (ltu/strip-unwanted-attrs (assoc template attr-kw attr-value))}

        uri           (str cfg/resource-type "/" service)
        abs-uri       (str p/service-context uri)]

    ;; verify that the auto-generated configuration is present
    (-> session-admin
        (request abs-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; admin create with valid template should fail
    ;; slipstream configuration initialization will have already created a resource
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 409))

    ;; admin delete succeeds
    (-> session-admin
        (request abs-uri
                 :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; ensure entry is really gone
    (-> session-admin
        (request abs-uri)
        (ltu/body->edn)
        (ltu/is-status 404))))


(deftest lifecycle-nuvla
  (check-existing-configuration ct-nuvla/service :support-email "admin@example.org")
  (test-utils/check-lifecycle ct-nuvla/service :support-email "admin@example.org" "admin@example.com"))
