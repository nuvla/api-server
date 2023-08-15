(ns sixsq.nuvla.server.resources.nuvlabox-status-0-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.nuvlabox-status-0 :as nb-status-0]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-status/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")


(def valid-nuvlabox {:owner nuvlabox-owner})


(def valid-state {:id                      (str nb-status/resource-type "/uuid")
                  :resource-type           nb-status/resource-type
                  :created                 timestamp
                  :updated                 timestamp

                  :version                 0
                  :status                  "OPERATIONAL"
                  :comment                 "some witty comment"

                  :next-heartbeat          timestamp

                  :resources               {:cpu       {:capacity 8
                                                        :load     4.5
                                                        :topic    "topic/name"}
                                            :ram       {:capacity   4096
                                                        :used       1000
                                                        :raw-sample "{\"one\": 1}"}
                                            :disks     [{:device     "root"
                                                         :capacity   20000
                                                         :topic      "topic/name"
                                                         :raw-sample "{\"one\": 1}"
                                                         :used       10000}
                                                        {:device   "datastore"
                                                         :capacity 20000
                                                         :used     10000}]
                                            :net-stats [{:interface         "eth0"
                                                         :bytes-received    5247943
                                                         :bytes-transmitted 41213
                                                         }
                                                        {:interface         "vpn"
                                                         :bytes-received    2213
                                                         :bytes-transmitted 55}]}

                  :peripherals             {:usb [{:vendor-id   "vendor-id"
                                                   :device-id   "device-id"
                                                   :bus-id      "bus-id"
                                                   :product-id  "product-id"
                                                   :description "description"}]}

                  :wifi-password           "some-secure-password"
                  :nuvlabox-api-endpoint   "https://1.2.3.4:1234"
                  :operating-system        "Ubuntu"
                  :architecture            "x86"
                  :hostname                "localhost"
                  :ip                      "127.0.0.1"
                  :docker-server-version   "19.0.3"
                  :last-boot               timestamp
                  :inferred-location       [46.2044 6.1432 373.]
                  :gpio-pins               [{:name    "GPIO. 7"
                                             :bcm     4
                                             :mode    "IN"
                                             :voltage 1
                                             :pin     7}
                                            {:pin 1}]
                  :nuvlabox-engine-version "1.2.3"
                  :swarm-node-id           "xyz"
                  :installation-parameters {:config-files   ["docker-compose.yml",
                                                             "docker-compose.usb.yaml"]
                                            :working-dir    "/home/user"
                                            :project-name   "nuvlabox"
                                            :environment    []}
                  :swarm-node-cert-expiry-date  "2020-02-18T19:42:08Z"})


(def resources-updated {:cpu   {:capacity   10
                                :load       5.5
                                :raw-sample "10.2"}
                        :ram   {:capacity 4096
                                :used     2000}
                        :disks [{:device   "root"
                                 :capacity 20000
                                 :used     20000}
                                {:device   "datastore"
                                 :capacity 20000
                                 :used     15000}]})

(def peripherals-updated {:usb [{:vendor-id   "vendor-id-2"
                                 :device-id   "device-id-2"
                                 :bus-id      "bus-id-2"
                                 :product-id  "product-id-2"
                                 :description "description-2"}]})


(deftest check-metadata
  (mdtu/check-metadata-exists nb-status/resource-type
                              (str nb-status/resource-type "-" nb-status-0/schema-version)))


(deftest lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

          nuvlabox-id   (-> session-user
                            (request nuvlabox-base-uri
                                     :request-method :post
                                     :body (json/write-str valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          valid-acl     {:owners    ["group/nuvla-admin"]
                         :edit-data [nuvlabox-id]}

          session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

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

          ;; admin edition doesn't set online flag
          (-> session-admin
              (request state-url
                       :request-method :put
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; nuvlabox user is able to update nuvlabox-status and :resources are rotated
          (let [resources-prev (-> session-nb
                                   (request state-url)
                                   (ltu/body->edn)
                                   (ltu/body)
                                   :resources)]
            (-> session-nb
                (request state-url
                         :request-method :put
                         :body (json/write-str {:resources resources-updated}))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :resources resources-updated)
                (ltu/is-key-value :resources-prev nil))

            (is (= resources-prev (:resources-prev (db/retrieve state-id {}))))

            ;; admin edition can set online flag
            ;; no :resources rotation as no update is done
            (-> session-admin
                (request state-url
                         :request-method :put
                         :body (json/write-str {:online false}))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :resources resources-updated)
                (ltu/is-key-value :resources-prev nil))

            (is (= resources-prev (:resources-prev (db/retrieve state-id {})))))


          (let [resources-prev (-> session-nb
                                   (request state-url)
                                   (ltu/body->edn)
                                   (ltu/body)
                                   :resources)]
            (-> session-nb
                (request state-url
                         :request-method :put
                         :body (json/write-str {:resources resources-updated}))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :resources resources-updated)
                (ltu/is-key-value :resources-prev nil))

            (is (= resources-prev (:resources-prev (db/retrieve state-id {})))))

          ;; verify that the update was written to disk
          (-> session-nb
              (request state-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :resources resources-updated))

          (let [resources-prev (-> session-nb
                                   (request state-url)
                                   (ltu/body->edn)
                                   (ltu/body)
                                   :resources)]
          (-> session-nb
              (request state-url
                       :request-method :put
                       :body (json/write-str {:peripherals peripherals-updated}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :peripherals peripherals-updated))

          (is (= resources-prev (:resources-prev (db/retrieve state-id {})))))

          ;; non of the items in the collection contain '-prev' keys
          (let [resp-resources (-> session-nb
                                   (request base-uri)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/is-count #(> % 0))
                                   (ltu/body)
                                   :resources)]
            (doseq [r resp-resources]
              (doseq [k nb-status/blacklist-response-keys]
                (is (not (contains? r k))))))

          ;; non of the items in the collection after search contain '-prev' keys
          (let [resp-resources (-> session-nb
                                   (content-type "application/x-www-form-urlencoded")
                                   (request base-uri
                                            :request-method :put
                                            :body (rc/form-encode {:filter "version='0'"}))
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/is-count #(> % 0))
                                   (ltu/body)
                                   :resources)]
            (doseq [r resp-resources]
              (doseq [k nb-status/blacklist-response-keys]
                (is (not (contains? r k))))))

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
      (let [response  (nb-status/create-nuvlabox-status 0 nuvlabox-id "name" {:owners   ["group/nuvla-admin"]
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


(deftest lifecycle-online-next-heartbeat
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

          nuvlabox-id   (-> session-user
                            (request nuvlabox-base-uri
                                     :request-method :post
                                     :body (json/write-str valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          nuvlabox-url  (str p/service-context nuvlabox-id)

          valid-acl     {:owners    ["group/nuvla-admin"]
                         :edit-data [nuvlabox-id]}

          session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

      ;; admin users can create a nuvlabox-status resource
      (when-let [state-id (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str (assoc valid-state :parent nuvlabox-id
                                                                                :acl valid-acl)))
                              (ltu/body->edn)
                              (ltu/is-status 201)
                              (ltu/body-resource-id))]

        (let [status-url     (str p/service-context state-id)
              ;; admin edition doesn't set online flag
              next-heartbeat (-> session-admin
                                 (request status-url
                                          :request-method :put
                                          :body (json/write-str {}))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/is-key-value :online nil)
                                 (ltu/is-key-value :next-heartbeat timestamp)
                                 (ltu/body)
                                 :next-heartbeat)]

          ;; nuvlabox user is able to update nuvlabox-status and online flag is set automatically
          ;; and hearbeat is updated
          (-> session-nb
              (request status-url
                       :request-method :put
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true)
              (ltu/is-key-value #(not= next-heartbeat %1) :next-heartbeat true))

          ;; online flag propagated to nuvlabox
          (-> session-admin
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true))

          ;; admin is able to set online flag to false
          (-> session-admin
              (request status-url
                       :request-method :put
                       :body (json/write-str {:online false}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online false))

          ;; online-prev is extracted from db because this attribute is not visible from cimi (blacklisted)
          (is (= true (:online-prev (db/retrieve state-id {}))))

          ;; online flag propagated to nuvlabox
          (-> session-admin
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online false))

          ;; nuvlabox can set online back
          (-> session-nb
              (request status-url
                       :request-method :put
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true))

          (is (= false (:online-prev (db/retrieve state-id {}))))

          ;; when a nuvlabox send telemetry that has a spec validation issue,
          ;; the heartbeat is still updated
          (let [status-prev (-> session-nb
                                (request status-url)
                                (ltu/body->edn)
                                (ltu/body))]
            (-> session-nb
                (request status-url
                         :request-method :put
                         :body (json/write-str {:wrong 1}))
                (ltu/body->edn)
                (ltu/is-status 400))

            (-> session-nb
                (request status-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value #(and (not= (:next-heartbeat status-prev) %)
                                        (string? %)) :next-heartbeat true)
                (ltu/is-key-value #(and (not= (:updated status-prev) %)
                                        (string? %)) :updated true)
                (ltu/is-key-value :online true)))
          )))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-status/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
