(ns com.sixsq.slipstream.ssclj.resources.session-template-internal-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.session-template :as st]
    [com.sixsq.slipstream.ssclj.resources.session-template-internal :as internal]
    [com.sixsq.slipstream.ssclj.resources.session-template-lifecycle-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.util.metadata-test-utils :as mdtu]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase st/resource-name)))


(def valid-template {:method
                                  internal/authn-method
                     :instance    internal/authn-method
                     :name        "Internal"
                     :description "Internal Authentication via Username/Password"
                     :username    "username"
                     :password    "password"
                     :acl         st/resource-acl})


(defn check-existing-session-template [base-uri valid-template]

  (let [method (:method valid-template)
        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-anon (header session authn-info-header "unknown ANON")]

    ;; should be an existing template already
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-template))
        (ltu/body->edn)
        (ltu/is-status 409))

    ;; session template should be visible via query as well
    ;; should have one with the correct method name
    (let [entries (-> session-anon
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-resource-uri st/collection-uri)
                      (ltu/entries :sessionTemplates))]
      (is (= 1 (count (filter #(= method (:method %)) entries)))))

    ;; do full lifecycle for an internal session template
    (let [uri (str (u/de-camelcase st/resource-name) "/" method)
          abs-uri (str p/service-context uri)]

      ;; delete the template
      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the template is gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404))

      ;; session template should not be there anymore
      (ltu/refresh-es-indices)
      (let [entries (-> session-anon
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri st/collection-uri)
                        (ltu/entries :sessionTemplates))]
        (is (zero? (count (filter #(= method (:method %)) entries))))))))


(deftest check-metadata
  (mdtu/check-metadata-exists (str st/resource-url "-" internal/resource-url)))


(deftest lifecycle
  (check-existing-session-template base-uri valid-template)
  (stu/session-template-lifecycle base-uri valid-template))
