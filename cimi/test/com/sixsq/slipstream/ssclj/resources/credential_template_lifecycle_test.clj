(ns com.sixsq.slipstream.ssclj.resources.credential-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.credential-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.credential-template-api-key :as akey]
    [com.sixsq.slipstream.ssclj.resources.credential-template-ssh-key-pair :as skp]
    [com.sixsq.slipstream.ssclj.resources.credential-template-ssh-public-key :as spk]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.util.metadata-test-utils :as mdtu]
    [com.sixsq.slipstream.ssclj.resources.credential-template-cloud-alpha :as alpha]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase ct/resource-name)))


(deftest check-retrieve-by-id
  (doseq [registration-method [spk/method skp/method akey/method]]
    (let [id (str ct/resource-url "/" registration-method)
          doc (crud/retrieve-by-id id)]
      (is (= id (:id doc))))))


(deftest check-metadata
  (mdtu/check-metadata-exists ct/resource-url)
  (mdtu/check-metadata-exists (str ct/resource-url "-" akey/resource-url))
  (mdtu/check-metadata-exists (str ct/resource-url "-" skp/resource-url))
  (mdtu/check-metadata-exists (str ct/resource-url "-" spk/resource-url)))


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
                    (ltu/entries ct/resource-tag))
        ids (set (map :id entries))
        methods (set (map :method entries))
        types (set (map :type entries))]
    (is (= #{(str ct/resource-url "/" spk/method)
             (str ct/resource-url "/" skp/method)
             (str ct/resource-url "/" akey/method)
             (str ct/resource-url "/" alpha/method)}
           ids))
    (is (= #{spk/method skp/method akey/method alpha/method} methods))
    (is (= #{spk/credential-type akey/credential-type alpha/credential-type} types))

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
