(ns sixsq.nuvla.server.resources.user-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user-template :as t]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest check-retrieve-by-id
  (doseq [registration-method [email-password/registration-method
                               username-password/registration-method]]
    (let [id (str t/resource-type "/" registration-method)
          doc (crud/retrieve-by-id id)]
      (is (= id (:id doc))))))


;; check that all templates are visible as administrator
(deftest lifecycle-admin

  (let [session (-> (session (ltu/ring-app))
                    (content-type "application/json")
                    (header authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon"))
        entries (-> session
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri t/collection-type)
                    (ltu/is-count pos?)
                    (ltu/is-operation-present "add")
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/entries))
        ids (set (map :id entries))
        types (set (map :method entries))]

    (is (= #{(str t/resource-type "/" email-password/registration-method)
             (str t/resource-type "/" username-password/registration-method)}
           ids))

    (is (= #{email-password/registration-method
             username-password/registration-method}
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
