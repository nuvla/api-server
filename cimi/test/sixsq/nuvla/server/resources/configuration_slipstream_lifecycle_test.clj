(ns sixsq.nuvla.server.resources.configuration-slipstream-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :refer :all]
    [sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as slipstream]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context resource-type))

;; must have specialized checks for slipstream configuration because
;; the initialization function creates a default slipstream configuration
;; resource

(defn check-existing-configuration
  [service attr-kw attr-value]

  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")

        template-url (str p/service-context ct/resource-type "/" service)
        resp (-> session-admin
                 (request template-url)
                 (ltu/body->edn)
                 (ltu/is-status 200))
        template (get-in resp [:response :body])
        valid-create {:template (ltu/strip-unwanted-attrs (assoc template attr-kw attr-value))}

        uri (str resource-type "/" service)
        abs-uri (str p/service-context uri)]

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


(deftest lifecycle-slipstream
  (check-existing-configuration slipstream/service :registrationEnable true)
  (test-utils/check-lifecycle slipstream/service :registrationEnable true false))
