(ns sixsq.nuvla.server.resources.user-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user-template :as t]
    [sixsq.nuvla.server.resources.user-template-direct :as direct]
    ;[sixsq.nuvla.server.resources.user-template-github-registration :as github]
    ;[sixsq.nuvla.server.resources.user-template-oidc-registration :as oidc]
    ;[sixsq.nuvla.server.resources.user-template-self-registration :as self]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest check-retrieve-by-id
  (doseq [registration-method [direct/registration-method]]
    (let [id (str t/resource-type "/" registration-method)
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
                    (ltu/is-resource-uri t/collection-type)
                    (ltu/is-count pos?)
                    (ltu/is-operation-present "add")        ;; should really be absent, but admin always has all rights
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/entries))
        ids (set (map :id entries))
        types (set (map :method entries))]
    (is (= #{(str t/resource-type "/" direct/registration-method)}
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
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
