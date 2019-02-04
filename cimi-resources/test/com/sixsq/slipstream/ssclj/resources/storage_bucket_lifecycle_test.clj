(ns com.sixsq.slipstream.ssclj.resources.storage-bucket-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.storage-bucket :as bucky]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context (u/de-camelcase bucky/resource-url)))


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

    ;; normal user collection query should succeed but be empty (no bucky created yet)
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


    ;; create a bucky as a normal user
    (let [timestamp "1964-08-25T10:00:00.0Z"
          create-test-bucky {:id             (str bucky/resource-url "/uuid")
                             :resourceURI    bucky/resource-uri
                             :created        timestamp
                             :updated        timestamp

                             :name           "short name"
                             :description    "short description",
                             :properties     {:a "one",
                                              :b "two"}

                             :bucketName     "aaa-bbb-111"
                             :usageInKiB     123456
                             :connector      {:href "connector/0123-4567-8912"}


                             :credentials    [{:href "credential/0123-4567-8912"}]


                             :externalObject {:href "external-object/aaa-bbb-ccc",
                                              :user {:href "user/test"}}

                             :serviceOffer   {:href              "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                              :resource:storage  1
                                              :resource:host     "s3-eu-west-1.amazonaws.com"
                                              :price:currency    "EUR"
                                              :price:unitCode    "HUR"
                                              :price:unitCost    "0.018"
                                              :resource:platform "S3"}}

          create-jane-bucky (-> create-test-bucky
                                (assoc :externalObject {:href "external-object/444-555-666"
                                                        :user {:href "user/jane"}})
                                (assoc :bucketName "otherName"))

          resp-test (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-test-bucky))
                        (ltu/body->edn)
                        (ltu/is-status 201))

          ; duplicated bucky will fail (with same instanceID and connector/href)
          resp-test2 (-> session-admin
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str create-test-bucky))
                         (ltu/body->edn)
                         (ltu/is-status 409))

          resp-jane (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-jane-bucky))
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
      (let [reread-test-bucky (-> session-admin
                                  (request test-uri)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  :response
                                  :body)]

        ;; Currency attribute should be copied from serviceOffer
        (is (= (-> reread-test-bucky :serviceOffer :price:currency) (:currency reread-test-bucky) ))
        (is (= (ltu/strip-unwanted-attrs reread-test-bucky) (ltu/strip-unwanted-attrs (assoc create-test-bucky :currency "EUR"))))


        (let [edited-test-bucky (-> session-admin
                                    (request test-uri
                                             :request-method :put
                                             :body (json/write-str (assoc reread-test-bucky :bucketName "NewName!")))
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    :response
                                    :body)]

          (is (= (assoc (ltu/strip-unwanted-attrs reread-test-bucky) :bucketName "NewName!")
                 (ltu/strip-unwanted-attrs edited-test-bucky)))))

      ;; disallowed edits
      (-> session-jane
          (request test-uri
                   :request-method :put
                   :body (json/write-str create-test-bucky))
          (ltu/body->edn)
          (ltu/is-status 403))

      (-> session-anon
          (request test-uri
                   :request-method :put
                   :body (json/write-str create-test-bucky))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; search
      (-> session-admin
          (request base-uri
                   :request-method :put
                   :body (json/write-str create-test-bucky))
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
  (let [resource-uri (str p/service-context (u/new-resource-id bucky/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
