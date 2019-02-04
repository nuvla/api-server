(ns com.sixsq.slipstream.ssclj.resources.resource-metadata-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as cu]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as t]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-test :as resource-metadata]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-url))

(def valid-acl {:owner {:principal "ADMIN",
                        :type      "ROLE"},
                :rules [{:principal "ADMIN",
                         :type      "ROLE",
                         :right     "MODIFY"},
                        {:principal "ANON",
                         :type      "ROLE",
                         :right     "VIEW"}]})

(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; anyone can query the metadata
    ;; because of automatic registration, the list may not be empty
    (doseq [session [session-admin session-user session-anon]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit")))

    ;; use the internal register method to create a new entry
    (let [identifier "unit-test-resource"
          full-identifier (str t/resource-url "/" identifier)
          abs-uri (str p/service-context full-identifier)]

      (t/register (-> resource-metadata/valid
                      (dissoc :acl)
                      (assoc :typeURI identifier)))

      (doseq [session [session-admin session-user session-anon]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-resource-uri t/collection-uri)
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
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-url))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [base-uri :post]
                            [resource-uri :options]
                            [resource-uri :post]
                            [resource-uri :put]
                            [resource-uri :delete]])))
