(ns com.sixsq.nuvla.server.resources.user-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.user-template :as t]
    [com.sixsq.nuvla.server.resources.user-template-email-invitation :as email-invitation]
    [com.sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [com.sixsq.nuvla.server.resources.user-template-github :as github]
    [com.sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [com.sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type
                              (str t/resource-type "-" github/resource-url)
                              (str t/resource-type "-" github/resource-url "-create")
                              (str t/resource-type "-" email-password/resource-url)
                              (str t/resource-type "-" email-password/resource-url "-create")
                              (str t/resource-type "-" username-password/resource-url)
                              (str t/resource-type "-" username-password/resource-url "-create")))


(deftest check-retrieve-by-id
  (let [id  (str t/resource-type "/" email-password/registration-method)
        doc (crud/retrieve-by-id id {:nuvla/authn {:claims #{"group/nuvla-anon"}}})]
    (is (= id (:id doc))))
  (let [id  (str t/resource-type "/" username-password/registration-method)
        doc (crud/retrieve-by-id-as-admin id)]
    (is (= id (:id doc)))))


;; check that all templates are visible as administrator
(deftest lifecycle-admin

  (let [session (-> (session (ltu/ring-app))
                    (content-type "application/json")
                    (header authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))
        entries (-> session
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri t/collection-type)
                    (ltu/is-count pos?)
                    (ltu/is-operation-present :add)
                    (ltu/is-operation-absent :delete)
                    (ltu/is-operation-absent :edit)
                    (ltu/entries))
        ids     (set (map :id entries))
        types   (set (map :method entries))]

    (is (= #{(str t/resource-type "/" minimum/registration-method)
             (str t/resource-type "/" email-password/registration-method)
             (str t/resource-type "/" username-password/registration-method)
             (str t/resource-type "/" email-invitation/registration-method)}
           ids))

    (is (= #{minimum/registration-method
             email-password/registration-method
             username-password/registration-method
             email-invitation/registration-method}
           types))

    (doseq [entry entries]
      (let [ops        (ltu/operations->map entry)
            entry-url  (str p/service-context (:id entry))

            entry-resp (-> session
                           (request entry-url)
                           (ltu/is-status 200)
                           (ltu/body->edn))

            entry-body (get-in entry-resp [:response :body])]

        (is (nil? (get ops (name :add))))
        (is (get ops (name :edit)))
        (is (get ops (name :delete)))

        (is (crud/validate entry-body))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
