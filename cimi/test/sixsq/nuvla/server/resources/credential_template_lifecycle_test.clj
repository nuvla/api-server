(ns sixsq.nuvla.server.resources.credential-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [sixsq.nuvla.server.resources.credential-template-cloud-alpha :as alpha]
    [sixsq.nuvla.server.resources.credential-template-cloud-docker :as docker]
    [sixsq.nuvla.server.resources.credential-template-service-exoscale :as service-exo]
    [sixsq.nuvla.server.resources.credential-template-service-gce :as service-gce]
    [sixsq.nuvla.server.resources.credential-template-service-aws :as service-aws]
    [sixsq.nuvla.server.resources.credential-template-service-swarm :as service-swarm]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context ct/resource-type))


(deftest check-retrieve-by-id
  (doseq [registration-method [akey/method]]
    (let [id (str ct/resource-type "/" registration-method)
          doc (crud/retrieve-by-id id)]
      (is (= id (:id doc))))))


(deftest check-metadata
  (mdtu/check-metadata-exists ct/resource-type)
  (mdtu/check-metadata-exists (str ct/resource-type "-" akey/resource-url)))


;; check that all templates are visible as normal user
(deftest lifecycle-admin
  (let [session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        entries (-> session-user
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri ct/collection-type)
                    (ltu/is-count pos?)
                    (ltu/is-operation-absent "add")
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/entries))
        ids (set (map :id entries))
        methods (set (map :method entries))
        types (set (map :type entries))]
    (is (= #{(str ct/resource-type "/" akey/method)
             (str ct/resource-type "/" alpha/method)
             (str ct/resource-type "/" docker/method)
             (str ct/resource-type "/" service-gce/method)
             (str ct/resource-type "/" service-exo/method)
             (str ct/resource-type "/" service-aws/method)
             (str ct/resource-type "/" service-swarm/method)}
           ids))
    (is (= #{akey/method
             alpha/method
             docker/method
             service-swarm/method
             service-exo/method
             service-aws/method
             service-gce/method} methods))
    (is (= #{akey/credential-type
             alpha/credential-type
             docker/credential-type
             service-swarm/credential-type
             service-exo/credential-type
             service-aws/credential-type
             service-gce/credential-type} types))

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
  (let [resource-uri (str p/service-context (u/new-resource-id ct/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :post]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
