(ns sixsq.nuvla.server.resources.credential-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [sixsq.nuvla.server.resources.credential-template-cloud-alpha :as alpha]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context ct/resource-name))


(deftest check-retrieve-by-id
  (doseq [registration-method [akey/method]]
    (let [id (str ct/resource-url "/" registration-method)
          doc (crud/retrieve-by-id id)]
      (is (= id (:id doc))))))


(deftest check-metadata
  (mdtu/check-metadata-exists ct/resource-url)
  (mdtu/check-metadata-exists (str ct/resource-url "-" akey/resource-url)))


;; check that all templates are visible as normal user
(deftest lifecycle-admin
  (let [session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        entries (-> session-user
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri ct/collection-uri)
                    (ltu/is-count pos?)
                    (ltu/is-operation-absent "add")
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/entries))
        ids (set (map :id entries))
        methods (set (map :method entries))
        types (set (map :type entries))]
    (is (= #{(str ct/resource-url "/" akey/method)
             (str ct/resource-url "/" alpha/method)}
           ids))
    (is (= #{akey/method alpha/method} methods))
    (is (= #{akey/credential-type alpha/credential-type} types))

    (doseq [entry entries]
      (let [ops (ltu/operations->map entry)
            entry-url (str p/service-context (:id entry))

            entry-resp (-> session-user
                           (request entry-url)
                           (ltu/is-status 200)
                           (ltu/body->edn))

            entry-body (get-in entry-resp [:response :body])]
        (is (nil? (get ops (c/action-uri :add))))
        (is (nil? (get ops (c/action-uri :edit))))
        (is (nil? (get ops (c/action-uri :delete))))

        (is (crud/validate entry-body))))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id ct/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :post]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
