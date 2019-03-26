(ns sixsq.nuvla.server.resources.resource-metadata-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.common.utils :as cu]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.resource-metadata :as t]
    [sixsq.nuvla.server.resources.spec.resource-metadata-test :as resource-metadata]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})

(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

    ;; anyone can query the metadata
    ;; because of automatic registration, the list may not be empty
    (doseq [session [session-admin #_session-user #_session-anon]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit")))

    ;; use the internal register method to create a new entry
    (let [identifier "unit-test-resource"
          full-identifier (str t/resource-type "/" identifier)
          abs-uri (str p/service-context full-identifier)]

      (t/register (-> resource-metadata/valid
                      (dissoc :acl)
                      (assoc :typeURI identifier)))

      (doseq [session [session-admin #_session-user #_session-anon]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-resource-uri t/collection-type)
            (ltu/is-count pos?))

        (let [{:keys [id] :as metadata} (-> session
                                            (request abs-uri)
                                            (ltu/body->edn)
                                            (ltu/is-status 200)
                                            (ltu/is-operation-absent "add")
                                            (ltu/is-operation-absent "edit")
                                            (ltu/is-operation-absent "delete")
                                            :response
                                            :body)]

          (is (= (cu/document-id id) identifier)))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [base-uri :post]
                            [resource-uri :options]
                            [resource-uri :post]
                            [resource-uri :put]
                            [resource-uri :delete]])))
