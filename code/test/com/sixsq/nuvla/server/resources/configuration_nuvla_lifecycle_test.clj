(ns com.sixsq.nuvla.server.resources.configuration-nuvla-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.configuration :as cfg]
    [com.sixsq.nuvla.server.resources.configuration-lifecycle-test-utils :as test-utils]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as t]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.configuration-template-nuvla :as ct-nuvla]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


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
        session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

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


(deftest test-is-not-authorized-url?
  (binding [t/*authorized-redirect-urls* nil]
    (are [result redirect-url] (= result (t/authorized-url? redirect-url))
                               true "https://nuvla.io"
                               true "https://nuvla.io/hello"
                               true "https://nuvla.io/hello/anything"
                               true "https://nuvla.io?param=1"
                               true "https://nuvla.io/hello/anything?parm=1&param=2"
                               true "http://nuvla.io"
                               true "https://phishing.com"
                               true "https://example.com  "
                               true ""))
  (binding [t/*authorized-redirect-urls* ["https://nuvla.io"]]
    (are [result redirect-url] (= result (t/authorized-url? redirect-url))
                               true "https://nuvla.io"
                               true "https://nuvla.io/hello"
                               true "https://nuvla.io/hello/anything"
                               true "https://nuvla.io?param=1"
                               true "https://nuvla.io/hello/anything?parm=1&param=2"
                               false "http://nuvla.io"
                               false "https://phishing.com"
                               false "https://example.com  "
                               false ""))
  (binding [t/*authorized-redirect-urls* ["https://nuvla.io" "http://nuvla.io"]]
    (are [result redirect-url] (= result (t/authorized-url? redirect-url))
                               true "https://nuvla.io"
                               true "https://nuvla.io/hello"
                               true "https://nuvla.io/hello/anything"
                               true "https://nuvla.io?param=1"
                               true "https://nuvla.io/hello/anything?parm=1&param=2"
                               true "http://nuvla.io"
                               false "https://phishing.com"
                               false "")))

(deftest lifecycle-nuvla
  (check-existing-configuration ct-nuvla/service :support-email "admin@example.org")
  (test-utils/check-lifecycle ct-nuvla/service :support-email "admin@example.org" "admin@example.com"))
