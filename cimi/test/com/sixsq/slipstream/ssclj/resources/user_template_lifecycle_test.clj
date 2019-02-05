(ns com.sixsq.slipstream.ssclj.resources.user-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.user-template :as t]
    [com.sixsq.slipstream.ssclj.resources.user-template-direct :as direct]
    ;[com.sixsq.slipstream.ssclj.resources.user-template-github-registration :as github]
    ;[com.sixsq.slipstream.ssclj.resources.user-template-oidc-registration :as oidc]
    ;[com.sixsq.slipstream.ssclj.resources.user-template-self-registration :as self]
    [com.sixsq.slipstream.ssclj.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context (u/de-camelcase t/resource-name)))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-url))


(deftest check-retrieve-by-id
  (doseq [registration-method [direct/registration-method]]
    (let [id (str t/resource-url "/" registration-method)
          doc (crud/retrieve-by-id id)]
      (is (= id (:id doc))))))


;; check that all templates are visible as administrator
;; only the 'direct' template will be created automatically
(deftest lifecycle-admin
  (let [session (-> (session (ltu/ring-app))
                    (content-type "application/json")
                    (header authn-info-header "root ADMIN"))
        entries (-> session
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri t/collection-uri)
                    (ltu/is-count pos?)
                    (ltu/is-operation-present "add")        ;; should really be absent, but admin always has all rights
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/entries t/resource-tag))
        ids (set (map :id entries))
        types (set (map :method entries))]
    (is (= #{(str t/resource-url "/" direct/registration-method)}
           ids))
    (is (= #{direct/registration-method}
           types))

    (doseq [entry entries]
      (let [ops (ltu/operations->map entry)
            entry-url (str p/service-context (:id entry))

            entry-resp (-> session
                           (request entry-url)
                           (ltu/is-status 200)
                           (ltu/body->edn))

            entry-body (get-in entry-resp [:response :body])]

        (is (nil? (get ops (c/action-uri :add))))
        (is (get ops (c/action-uri :edit)))
        (is (get ops (c/action-uri :delete)))

        (is (crud/validate entry-body))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
