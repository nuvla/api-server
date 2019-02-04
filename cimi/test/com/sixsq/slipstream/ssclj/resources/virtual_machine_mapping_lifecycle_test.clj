(ns com.sixsq.slipstream.ssclj.resources.virtual-machine-mapping-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.virtual-machine-mapping :as vmm]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase vmm/resource-url)))

(deftest lifecycle
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-jane (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")]

    ;; admin collection query should succeed but be empty (no  records created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; normal collection query should not succeed
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anonymous collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; creating a virtual machine mapping as a normal user should fail
    (let [create-request {:name         "short name"
                          :description  "short description",
                          :properties   {:a "one",
                                         :b "two"}

                          :cloud        "exoscale-ch-gva"
                          :instanceID   "aaa-bbb-111"

                          :runUUID      "run/b836e665-74df-4800-89dc-c746c335a6a9"
                          :owner        "user/janedoe"
                          :serviceOffer {:href "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"}}]

      (-> session-jane
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-request))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; create a vm as a normal user
    (let [cloud "exoscale-ch-gva"
          instanceID "aaa-bbb-111"
          create-request {:name         "short name"
                          :description  "short description",
                          :properties   {:a "one",
                                         :b "two"}

                          :cloud        cloud
                          :instanceID   instanceID

                          :runUUID      "run/b836e665-74df-4800-89dc-c746c335a6a9"
                          :owner        "user/janedoe"
                          :serviceOffer {:href "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"}}

          resp-admin (-> session-admin
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str create-request))
                         (ltu/body->edn)
                         (ltu/is-status 201))

          id-admin (get-in resp-admin [:response :body :resource-id])
          location-admin (str p/service-context (-> resp-admin ltu/location))
          uri-admin (str p/service-context id-admin)]

      (is (= (str vmm/resource-url "/" cloud "-" instanceID) id-admin))

      (is (= location-admin uri-admin))

      ;; adding the same resource again should give a conflict
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-request))
          (ltu/body->edn)
          (ltu/is-status 409))

      ;; admin should be able to see the resource
      (-> session-admin
          (request uri-admin)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-absent "edit"))

      ;; jane should not be able to see the resource
      (-> session-jane
          (request uri-admin)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; no one should not be able to edit the resource
      (-> session-admin
          (request uri-admin
                   :request-method :put
                   :body (json/write-str (assoc create-request :cloud "other-cloud")))
          (ltu/body->edn)
          (ltu/is-status 405))

      (-> session-jane
          (request uri-admin
                   :request-method :put
                   :body (json/write-str create-request))
          (ltu/body->edn)
          (ltu/is-status 405))

      (-> session-anon
          (request uri-admin
                   :request-method :put
                   :body (json/write-str create-request))
          (ltu/body->edn)
          (ltu/is-status 405))

      ;; check that the administrator can search and find the resource
      (-> session-admin
          (request base-uri
                   :request-method :put
                   :body (json/write-str create-request))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1))

      ;; jane cannot delete the resource
      (-> session-jane
          (request uri-admin
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; admin can delete the resource
      (-> session-admin
          (request uri-admin
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check that the resource is gone
      (-> session-admin
          (request uri-admin
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id vmm/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :put]
                            [resource-uri :post]])))
