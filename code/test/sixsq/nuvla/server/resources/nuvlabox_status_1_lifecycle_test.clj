(ns sixsq.nuvla.server.resources.nuvlabox-status-1-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.nuvlabox-status-1 :as nb-status-1]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-status/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")


(def valid-nuvlabox {:owner nuvlabox-owner})


(def valid-state {:id                    (str nb-status/resource-type "/uuid")
                  :resource-type         nb-status/resource-type
                  :created               timestamp
                  :updated               timestamp

                  :version               1
                  :status                "OPERATIONAL"
                  :comment               "some witty comment"

                  :next-heartbeat        timestamp

                  :resources             {:cpu   {:capacity 8
                                                  :load     4.5}
                                          :ram   {:capacity 4096
                                                  :used     1000}
                                          :disks [{:device   "root"
                                                   :capacity 20000
                                                   :used     10000}
                                                  {:device   "datastore"
                                                   :capacity 20000
                                                   :used     10000}]}

                  :wifi-password         "some-secure-password"
                  :nuvlabox-api-endpoint "https://1.2.3.4:1234"})


(def resources-updated {:cpu   {:capacity 10
                                :load     5.5}
                        :ram   {:capacity 4096
                                :used     2000}
                        :disks [{:device   "root"
                                 :capacity 20000
                                 :used     20000}
                                {:device   "datastore"
                                 :capacity 20000
                                 :used     15000}]})


(deftest check-metadata
  (mdtu/check-metadata-exists nb-status/resource-type
                              (str nb-status/resource-type "-" nb-status-1/schema-version)))


(deftest lifecycle

  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "user/unknown group/nuvla-anon")

        nuvlabox-id   (-> session-user
                          (request nuvlabox-base-uri
                                   :request-method :post
                                   :body (json/write-str valid-nuvlabox))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

        valid-acl     {:owners    ["group/nuvla-admin"]
                       :edit-data [nuvlabox-id]}

        session-nb    (header session authn-info-header (str nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

    ;; non-admin users cannot create a nuvlabox-status resource
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-state :parent nuvlabox-id
                                                            :acl valid-acl)))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; admin users can create a nuvlabox-status resource
    (when-let [state-id (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str (assoc valid-state :parent nuvlabox-id
                                                                              :acl valid-acl)))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/body-resource-id))]

      (let [state-url (str p/service-context state-id)]

        ;; other users cannot see the state
        (-> session-user
            (request state-url)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; nuvlabox user is able to update nuvlabox-status
        (-> session-nb
            (request state-url
                     :request-method :put
                     :body (json/write-str {:resources resources-updated}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :resources resources-updated))

        ;; verify that the update was written to disk
        (-> session-nb
            (request state-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :resources resources-updated))

        ;; nuvlabox identity cannot delete the state
        (-> session-nb
            (request state-url
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; administrator can delete the state
        (-> session-admin
            (request state-url
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))


    ;; verify that the internal create function also works
    (let [response  (nb-status/create-nuvlabox-status 0 nuvlabox-id {:owners   ["group/nuvla-admin"]
                                                                     :edit-acl ["user/alpha"]})
          location  (get-in response [:headers "Location"])
          state-id  (-> response :body :resource-id)
          state-url (str p/service-context state-id)]

      (is location)
      (is state-id)
      (is (= state-id location))

      ;; verify that the resource exists
      (-> session-nb
          (request state-url)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; administrator can delete the state
      (-> session-admin
          (request state-url
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-status/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
