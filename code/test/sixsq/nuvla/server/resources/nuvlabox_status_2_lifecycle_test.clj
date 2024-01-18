(ns sixsq.nuvla.server.resources.nuvlabox-status-2-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
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
    [sixsq.nuvla.server.resources.nuvlabox-status-2 :as nb-status-2]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.ts-nuvlaedge :as ts-nuvlaedge]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [sixsq.nuvla.server.util.time :as time]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-status/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def timestamp (time/now-str))


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")


(def nb-name "nb-test-status")

(def valid-nuvlabox {:name  nb-name
                     :owner nuvlabox-owner})


(def valid-state
  {:id                          (str nb-status/resource-type "/uuid")
   :resource-type               nb-status/resource-type
   :current-time                timestamp
   :created                     timestamp
   :updated                     timestamp

   :version                     2
   :status                      "OPERATIONAL"

   :resources                   {:cpu             {:capacity 8
                                                   :load     4.5}
                                 :ram             {:capacity 4096
                                                   :used     1000}
                                 :disks           [{:device   "root"
                                                    :capacity 20000
                                                    :used     10000}
                                                   {:device   "datastore"
                                                    :capacity 20000
                                                    :used     10000}]
                                 :net-stats       [{:interface         "eth0"
                                                    :bytes-received    5247943
                                                    :bytes-transmitted 41213
                                                    }
                                                   {:interface         "vpn"
                                                    :bytes-received    2213
                                                    :bytes-transmitted 55}]
                                 :container-stats []}

   :wifi-password               "some-secure-password"
   :nuvlabox-api-endpoint       "https://1.2.3.4:1234"
   :operating-system            "Ubuntu"
   :architecture                "x86"
   :hostname                    "localhost"
   :ip                          "127.0.0.1"
   :docker-server-version       "19.0.3"
   :last-boot                   timestamp
   :inferred-location           [46.2044 6.1432 373.]
   :gpio-pins                   [{:name    "GPIO. 7"
                                  :bcm     4
                                  :mode    "IN"
                                  :voltage 1
                                  :pin     7}
                                 {:pin 1}]
   :nuvlabox-engine-version     "1.2.3"
   :node-id                     "xyz"
   :cluster-id                  "123xyz"
   :installation-parameters     {:config-files ["docker-compose.yml",
                                                "docker-compose.usb.yaml"]
                                 :working-dir  "/home/user"
                                 :project-name "nuvlabox"
                                 :environment  []}
   :swarm-node-cert-expiry-date "2020-02-18T19:42:08Z"
   :host-user-home              "/home/user"
   :cluster-node-role           "worker"
   :status-notes                ["Lost quorum", "Swap disabled"]
   :cluster-nodes               ["syz", "xyz", "1dsdr3"]
   :cluster-managers            ["syz"]
   :cluster-join-address        "194.182.171.166:2377"
   :orchestrator                "kubernetes"
   :temperatures                []
   :components                  ["agent"]
   :network                     {:default-gw "eth0"
                                 :ips        {:local "1.2.3.4"}
                                 :interfaces [{:interface "eth0"
                                               :ips       [{:address "1.2.3.4"}]}]}})


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
                              (str nb-status/resource-type "-" nb-status-2/schema-version)))


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

      (testing "normal users cannot create a nuvlabox-status resource"
        (doseq [session [session-anon session-user]]
          (-> session
              (request base-uri
                       :request-method :post
                       :body (json/write-str (assoc valid-state :parent nuvlabox-id
                                                                :acl valid-acl)))
              (ltu/body->edn)
              (ltu/is-status 403))))

      (when-let [status-id (-> session-admin
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str (assoc valid-state :parent nuvlabox-id
                                                                                 :acl valid-acl)))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/body-resource-id))]

        (let [status-url (str p/service-context status-id)]
          (testing "other users cannot see the state"
            (-> session-user
                (request status-url)
                (ltu/body->edn)
                (ltu/is-status 403)))

          (testing "nuvlabox user is able to update nuvlabox-status and :resources are rotated"
            (let [resources-prev (-> session-nb
                                     (request status-url)
                                     (ltu/body->edn)
                                     (ltu/body)
                                     :resources)]
              (testing "when an update is done by a nuvlabox, the body contains only jobs"
                (is (= (-> session-nb
                           (request status-url
                                    :request-method :put
                                    :body (json/write-str {:resources resources-updated}))
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           ltu/body)
                       {:jobs []})))
              (let [nb-status (db/retrieve status-id)]
                (is (= resources-updated (:resources nb-status)))
                (is (= resources-prev (:resources-prev nb-status))))
              (testing "new metrics data is added to ts-nuvlaedge time-serie"
                (ltu/refresh-es-indices)
                (let [ts-nuvlaedge-url (str p/service-context ts-nuvlaedge/resource-type)
                      metric-data      (-> session-admin
                                           (content-type "application/x-www-form-urlencoded")
                                           (request ts-nuvlaedge-url
                                                    :request-method :put
                                                    :body (rc/form-encode
                                                            {:last 0
                                                             :tsds-aggregation
                                                             (json/write-str
                                                               {:aggregations
                                                                {:tsds-stats
                                                                 {:date_histogram
                                                                  {:field          "@timestamp"
                                                                   :fixed_interval "1d"}
                                                                  :aggregations
                                                                  {:avg-ram {:avg {:field :ram.used}}}}}})}))
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/body))]
                  (is (= 2000.0 (get-in metric-data [:aggregations :tsds-stats :buckets 0 :avg-ram :value])))))))

          (testing "verify that the update was written to disk"
            (-> session-nb
                (request status-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :name nb-name)
                (ltu/is-key-value :resources resources-updated)))

          (testing "non of the items in the collection contain '-prev' keys"
            (let [resp-resources (-> session-nb
                                     (request base-uri)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-count #(> % 0))
                                     (ltu/body)
                                     :resources)]
              (doseq [r resp-resources]
                (doseq [k nb-status/blacklist-response-keys]
                  (is (not (contains? r k)))))))

          (testing "non of the items in the collection after search contain '-prev' keys"
            (let [resp-resources (-> session-nb
                                     (content-type "application/x-www-form-urlencoded")
                                     (request base-uri
                                              :request-method :put
                                              :body (rc/form-encode {:filter "version='2'"}))
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-count #(> % 0))
                                     (ltu/body)
                                     :resources)]
              (doseq [r resp-resources]
                (doseq [k nb-status/blacklist-response-keys]
                  (is (not (contains? r k)))))))

          (testing "nuvlabox identity cannot delete the state"
            (-> session-nb
                (request status-url
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 403)))


          (testing "administrator can delete the state"
            (-> session-admin
                (request status-url
                         :request-method :delete)
                (ltu/body->edn)
                (ltu/is-status 200)))))

      (testing "verify that the internal create function also works"
        (let [response  (nb-status/create-nuvlabox-status
                          {:id      nuvlabox-id
                           :name    "name"
                           :version 0
                           :acl     {:owners   ["group/nuvla-admin"]
                                     :edit-acl ["user/alpha"]}})
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
              (ltu/is-status 200)))))))


(defn test-online-next-heartbeat
  []
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

          session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

      (-> session-nb
          (request (-> session-nb
                       (request nuvlabox-url)
                       (ltu/body->edn)
                       (ltu/is-status 200)
                       (ltu/get-op-url :activate)))
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [status-url (str p/service-context
                            (-> session-nb
                                (request nuvlabox-url)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                ltu/body
                                :nuvlabox-status))]

        (testing "nuvlabox name is set as name of nuvlabox status"
          (-> session-nb
              (request status-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :name nb-name)))

        (-> session-nb
            (request (-> session-nb
                         (request nuvlabox-url)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/get-op-url :commission))
                     :request-method :put
                     :body (json/write-str
                             {:capabilities [nb-utils/capability-job-pull]}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (testing "admin edition doesn't set online flag"
          (-> session-admin
              (request status-url
                       :request-method :put
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online nil)
              (ltu/is-key-value :last-heartbeat nil)
              (ltu/is-key-value :next-heartbeat nil)))

        (-> session-admin
            (request status-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        (testing "nuvlabox can do a legacy heartbeat"
          (-> session-nb
              (request status-url
                       :request-method :put
                       :body (json/write-str {:nuvlabox-engine-version "1.0.2"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :jobs []))
          (-> session-user
              (request status-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true)
              (ltu/is-key-value some? :next-heartbeat true)
              (ltu/is-key-value some? :last-heartbeat true))

          (testing "online flag is propagated to nuvlabox"
            (-> session-admin
                (request nuvlabox-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :online true))))

        (testing "nuvlabox get jobs on heartbeat"
          (-> session-nb
              (request (-> session-nb
                           (request nuvlabox-url)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/get-op-url :reboot)))
              (ltu/body->edn)
              (ltu/is-status 202))

          (-> session-nb
              (request status-url
                       :request-method :put
                       :body (json/write-str {:nuvlabox-engine-version "1.0.2"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value count :jobs 1)))

        (testing "admin can set offline"
          (-> session-admin
              (request (-> session-admin
                           (request nuvlabox-url)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/get-op-url nb-utils/action-set-offline)))
              (ltu/body->edn)
              (ltu/is-status 200)))

        (-> session-admin
            (request status-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :online false)
            (ltu/is-key-value some? :last-heartbeat true)
            (ltu/is-key-value some? :next-heartbeat true))

        (testing "when a nuvlabox send telemetry that has a spec validation
          issue, the heartbeat is still updated"
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
              (ltu/is-key-value :online true)
              (ltu/is-key-value string? :last-heartbeat true)
              (ltu/is-key-value string? :next-heartbeat true))

          (-> session-admin
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true)))))))

(deftest lifecycle-online-next-heartbeat
  (test-online-next-heartbeat))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-status/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
