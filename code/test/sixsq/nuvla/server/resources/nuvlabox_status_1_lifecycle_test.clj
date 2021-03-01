(ns sixsq.nuvla.server.resources.nuvlabox-status-1-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
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
                                                   :used     10000}]
                                          :net-stats [{:interface "eth0"
                                                       :bytes-received    5247943
                                                       :bytes-transmitted 41213
                                                       }
                                                      {:interface "vpn"
                                                       :bytes-received    2213
                                                       :bytes-transmitted 55}]}

                  :wifi-password         "some-secure-password"
                  :nuvlabox-api-endpoint "https://1.2.3.4:1234"
                  :operating-system      "Ubuntu"
                  :architecture          "x86"
                  :hostname              "localhost"
                  :ip                    "127.0.0.1"
                  :docker-server-version "19.0.3"
                  :last-boot             timestamp
                  :inferred-location     [46.2044 6.1432 373.]
                  :gpio-pins             [{:name  "GPIO. 7"
                                           :bcm   4
                                           :mode  "IN"
                                           :voltage 1
                                           :pin   7}
                                          {:pin   1}]
                  :nuvlabox-engine-version  "1.2.3"
                  :swarm-node-id           "xyz"
                  :installation-parameters {:config-files   ["docker-compose.yml",
                                                             "docker-compose.usb.yaml"]
                                            :working-dir    "/home/user"
                                            :project-name   "nuvlabox"
                                            :environment    []}
                  :swarm-node-cert-expiry-date  "2020-02-18T19:42:08Z"})


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
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                           session
                           (content-type "application/json"))
         session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")
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

       (let [status-url (str p/service-context state-id)]

         ;; other users cannot see the state
         (-> session-user
             (request status-url)
             (ltu/body->edn)
             (ltu/is-status 403))

         ;; admin edition doesn't set online flag
         (-> session-admin
             (request status-url
                      :request-method :put
                      :body (json/write-str {}))
             (ltu/body->edn)
             (ltu/is-status 200)
             (ltu/is-key-value :online nil))

         ;; nuvlabox user is able to update nuvlabox-status and :resources are rotated
         (let [resources-prev (-> session-nb
                                  (request status-url)
                                  (ltu/body->edn)
                                  (ltu/body)
                                  :resources)]
           (-> session-nb
               (request status-url
                        :request-method :put
                        :body (json/write-str {:resources resources-updated}))
               (ltu/body->edn)
               (ltu/is-status 200)
               (ltu/is-key-value :resources resources-updated)
               (ltu/is-key-value :resources-prev nil)
               (ltu/is-key-value :online-prev nil)
               (ltu/is-key-value :online true))

           (is (= resources-prev (:resources-prev (db/retrieve state-id {}))))

           ;; admin edition can set online flag
           ;; no :resources rotation as no update is done
           (-> session-admin
               (request status-url
                        :request-method :put
                        :body (json/write-str {:online false}))
               (ltu/body->edn)
               (ltu/is-status 200)
               (ltu/is-key-value :online-prev nil)
               (ltu/is-key-value :online false)
               (ltu/is-key-value :resources resources-updated)
               (ltu/is-key-value :resources-prev nil))

           (is (= true (:online-prev (db/retrieve state-id {}))))
           (is (= resources-prev (:resources-prev (db/retrieve state-id {})))))

         (let [resources-prev (-> session-nb
                                  (request status-url)
                                  (ltu/body->edn)
                                  (ltu/body)
                                  :resources)]
           (-> session-nb
               (request status-url
                        :request-method :put
                        :body (json/write-str {:resources resources-updated}))
               (ltu/body->edn)
               (ltu/is-status 200)
               (ltu/is-key-value :online-prev nil)
               (ltu/is-key-value :online true)
               (ltu/is-key-value :resources resources-updated)
               (ltu/is-key-value :resources-prev nil))

           (is (= false (:online-prev (db/retrieve state-id {}))))
           (is (= resources-prev (:resources-prev (db/retrieve state-id {})))))

         ;; verify that the update was written to disk
         (-> session-nb
             (request status-url)
             (ltu/body->edn)
             (ltu/is-status 200)
             (ltu/is-key-value :resources resources-updated))

         ;; nuvlabox identity cannot delete the state
         (-> session-nb
             (request status-url
                      :request-method :delete)
             (ltu/body->edn)
             (ltu/is-status 403))

         ;; administrator can delete the state
         (-> session-admin
             (request status-url
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
           (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-status/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
