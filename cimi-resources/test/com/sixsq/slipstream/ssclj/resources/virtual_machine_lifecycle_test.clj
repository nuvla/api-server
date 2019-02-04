(ns com.sixsq.slipstream.ssclj.resources.virtual-machine-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.virtual-machine :as vm]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase vm/resource-url)))


(deftest lifecycle
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-jane (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")]

    ;; admin user collection query should succeed but be empty (no  records created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; normal user collection query should succeed but be empty (no vm created yet)
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-absent "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))


    ;; anonymous credential collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))


    ;; create a vm as a normal user
    (let [timestamp "1964-08-25T10:00:00.0Z"
          create-test-vm {:id           (str vm/resource-url "/uuid")
                          :resourceURI  vm/resource-uri
                          :created      timestamp
                          :updated      timestamp

                          :name         "short name"
                          :description  "short description",
                          :properties   {:a "one",
                                         :b "two"}

                          :instanceID   "aaa-bbb-111"
                          :connector    {:href "connector/0123-4567-8912"}
                          :state        "Running"
                          :billable     true
                          :ip           "127.0.0.1"


                          :credentials  [{:href  "credential/0123-4567-8912",
                                          :roles ["realm:cern", "realm:my-accounting-group"]
                                          :users ["long-user-id-1", "long-user-id-2"]}]


                          :deployment   {:href "run/aaa-bbb-ccc",
                                         :user {:href "user/test"}}

                          :serviceOffer {:href                  "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                         :resource:vcpu         1
                                         :resource:ram          4096
                                         :resource:disk         10
                                         :resource:instanceType "Large"
                                         :price:currency        "EUR"}}

          create-jane-vm (-> create-test-vm
                             (assoc :deployment {:href "run/444-555-666"
                                                 :user {:href "user/jane"}})
                             (assoc :instanceID "otherID"))

          resp-test (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-test-vm))
                        (ltu/body->edn)
                        (ltu/is-status 201))

          ; duplicated vm will fail (with same instanceID and connector/href)
          resp-test2 (-> session-admin
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str create-test-vm))
                         (ltu/body->edn)
                         (ltu/is-status 409))

          resp-jane (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-jane-vm))
                        (ltu/body->edn)
                        (ltu/is-status 201))

          id-test (get-in resp-test [:response :body :resource-id])
          id-jane (get-in resp-jane [:response :body :resource-id])

          location-test (str p/service-context (-> resp-test ltu/location))
          location-jane (str p/service-context (-> resp-jane ltu/location))

          test-uri (str p/service-context id-test)
          jane-uri (str p/service-context id-jane)]

      (is (= location-test test-uri))
      (is (= location-jane jane-uri))

      ;; admin should be able to see everyone's records
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      (-> session-admin
          (request jane-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      (-> session-jane
          (request jane-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit"))

      ;; check contents and editing
      (let [reread-test-vm (-> session-admin
                               (request test-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               :response
                               :body)]

        ;; Currency attribute should be copied from serviceOffer
        (is (= (-> reread-test-vm :serviceOffer :price:currency) (:currency reread-test-vm)))
        (is (= (ltu/strip-unwanted-attrs reread-test-vm) (ltu/strip-unwanted-attrs (assoc create-test-vm :currency "EUR"))))

        (let [edited-test-vm (-> session-admin
                                 (request test-uri
                                          :request-method :put
                                          :body (json/write-str (assoc reread-test-vm :state "UPDATED!")))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 :response
                                 :body)]

          (is (= (assoc (ltu/strip-unwanted-attrs reread-test-vm) :state "UPDATED!")
                 (ltu/strip-unwanted-attrs edited-test-vm)))))

      ;; disallowed edits
      (-> session-jane
          (request test-uri
                   :request-method :put
                   :body (json/write-str create-test-vm))
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-anon
          (request test-uri
                   :request-method :put
                   :body (json/write-str create-test-vm))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; search
      (-> session-admin
          (request base-uri
                   :request-method :put
                   :body (json/write-str create-test-vm))
          (ltu/body->edn)
          (ltu/is-count 2)
          (ltu/is-status 200))

      ;;delete
      (-> session-jane
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request jane-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;;record should be deleted
      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id vm/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
