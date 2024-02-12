(ns sixsq.nuvla.server.resources.nuvlabox-status-2-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
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
                                                    :bytes-transmitted 41213}
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


(def resources-updated {:cpu               {:capacity 10
                                            :load     5.5}
                        :ram               {:capacity 4096
                                            :used     2000}
                        :disks             [{:device   "root"
                                             :capacity 20000
                                             :used     20000}
                                            {:device   "datastore"
                                             :capacity 20000
                                             :used     15000}]
                        :net-stats         [{:interface         "eth0"
                                             :bytes-received    5579821
                                             :bytes-transmitted 44145}
                                            {:interface         "vpn"
                                             :bytes-received    3019
                                             :bytes-transmitted 78}]
                        :power-consumption [{:metric-name        "IN_current"
                                             :energy-consumption 2.4
                                             :unit               "A"}]})


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
          _nuvlabox-url (str p/service-context nuvlabox-id)

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
                                     (ltu/is-key-value :swarm-enabled nil)
                                     (ltu/body)
                                     :resources)]
              (testing "when an update is done by a nuvlabox, the body contains only jobs"
                (is (= (-> session-nb
                           (request status-url
                                    :request-method :put
                                    :body (json/write-str {:current-time (time/now-str)
                                                           :online       true
                                                           :resources    resources-updated}))
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           ltu/body)
                       {:jobs []})))
              (let [nb-status (db/retrieve status-id)]
                (is (= resources-updated (:resources nb-status)))
                (is (= resources-prev (:resources-prev nb-status))))))

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
          (let [called (atom false)]
            (with-redefs [ts-nuvlaedge/bulk-insert-impl (fn [& _] (reset! called true))]
              (-> session-admin
                  (request status-url
                           :request-method :put
                           :body (json/write-str {}))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :online nil)
                  (ltu/is-key-value :last-heartbeat nil)
                  (ltu/is-key-value :next-heartbeat nil)))
            (is (false? @called) "metrics were inserted, but they should not have")))

        (-> session-admin
            (request status-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        (testing "nuvlabox can do a legacy heartbeat"
          (let [called (atom false)]
            (with-redefs [ts-nuvlaedge/bulk-insert-impl (fn [& _] (reset! called true))]
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
                  (ltu/is-key-value some? :last-heartbeat true)))
            (is (true? @called) "metrics were not inserted, but they should have"))

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
          (let [called (atom false)]
            (with-redefs [ts-nuvlaedge/bulk-insert-impl (fn [& _] (reset! called true))]
              (-> session-admin
                  (request (-> session-admin
                               (request nuvlabox-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url nb-utils/action-set-offline)))
                  (ltu/body->edn)
                  (ltu/is-status 200))
              (-> session-admin
                  (request status-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :online false)
                  (ltu/is-key-value some? :last-heartbeat true)
                  (ltu/is-key-value some? :next-heartbeat true))
              (is (true? @called) "metrics were not inserted, but they should have"))))

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

(deftest metric-data
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

          session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))
          status-id     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str (assoc valid-state :parent nuvlabox-id
                                                                              :acl valid-acl)))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/body-resource-id))
          status-url    (str p/service-context
                             status-id)]

      ;; update the nuvlabox
      (-> session-nb
          (request status-url
                   :request-method :put
                   :body (json/write-str {:current-time (time/now-str)
                                          :online       true
                                          :resources    resources-updated}))
          (ltu/body->edn)
          (ltu/is-status 200)
          ltu/body)

      (testing "metrics data on a single nuvlabox"
        (let [nuvlabox-data-url (str nuvlabox-url "/data")
              now               (time/now)
              metrics-request   (fn [{:keys [datasets from from-str to to-str granularity accept-header] #_:or #_{accept-header "application/json"}}]
                                  (-> session-nb
                                      (content-type "application/x-www-form-urlencoded")
                                      (cond-> accept-header (header "accept" accept-header))
                                      (request nuvlabox-data-url
                                               :body (rc/form-encode
                                                       {:dataset     datasets
                                                        :from        (if from (time/to-str from) from-str)
                                                        :to          (if to (time/to-str to) to-str)
                                                        :granularity granularity}))))]
          (testing "new metrics data is added to ts-nuvlaedge time-serie"
            (ltu/refresh-es-indices)
            (let [from        (time/minus (time/now) (time/duration-unit 1 :days))
                  to          now
                  metric-data (-> (metrics-request {:datasets    ["online-status-stats"
                                                                  "cpu-stats"
                                                                  "ram-stats"
                                                                  "disk-stats"
                                                                  "network-stats"
                                                                  "power-consumption-stats"]
                                                    :from        from
                                                    :to          to
                                                    :granularity "1-days"})
                                  (ltu/is-status 200)
                                  (ltu/body->edn)
                                  (ltu/body))]
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    1
                                     :aggregations {:avg-online {:value 1.0}}}]}]
                     (:online-status-stats metric-data)))
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    1
                                     :aggregations {:avg-cpu-capacity    {:value 10.0}
                                                    :avg-cpu-load        {:value 5.5}
                                                    :avg-cpu-load-1      {:value nil}
                                                    :avg-cpu-load-5      {:value nil}
                                                    :context-switches    {:value nil}
                                                    :interrupts          {:value nil}
                                                    :software-interrupts {:value nil}
                                                    :system-calls        {:value nil}}}]}]
                     (:cpu-stats metric-data)))
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    1
                                     :aggregations {:avg-ram-capacity {:value 4096.0}
                                                    :avg-ram-used     {:value 2000.0}}}]}]
                     (:ram-stats metric-data)))
              (is (= #{{:dimensions {:nuvlaedge-id nuvlabox-id
                                     :disk.device  "root"}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    1
                                      :aggregations {:avg-disk-capacity {:value 20000.0}
                                                     :avg-disk-used     {:value 20000.0}}}]}
                       {:dimensions {:nuvlaedge-id nuvlabox-id
                                     :disk.device  "datastore"}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    1
                                      :aggregations {:avg-disk-capacity {:value 20000.0}
                                                     :avg-disk-used     {:value 15000.0}}}]}}
                     (set (:disk-stats metric-data))))
              (is (= #{{:dimensions {:nuvlaedge-id      nuvlabox-id
                                     :network.interface "eth0"}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    1
                                      :aggregations {:bytes-received    {:value 5579821.0}
                                                     :bytes-transmitted {:value 44145.0}}}]}
                       {:dimensions {:nuvlaedge-id      nuvlabox-id
                                     :network.interface "vpn"}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    1
                                      :aggregations {:bytes-received    {:value 3019.0}
                                                     :bytes-transmitted {:value 78.0}}}]}}
                     (set (:network-stats metric-data))))
              (is (= #{{:dimensions {:nuvlaedge-id                  nuvlabox-id
                                     :power-consumption.metric-name "IN_current"}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    1
                                      :aggregations {:energy-consumption {:value 2.4}
                                                     #_:unit                 #_"A"}}]}}
                     (set (:power-consumption-stats metric-data))))))

          (testing "query request validation"
            (let [invalid-request (fn [options]
                                    (-> (metrics-request options)
                                        (ltu/is-status 400)
                                        (ltu/body->edn)
                                        (ltu/body)
                                        :message))]
              (is (= "format not supported: text/plain"
                     (invalid-request {:accept-header "text/plain"
                                       :datasets      ["cpu-stats"]
                                       :from          (time/minus now (time/duration-unit 1 :days))
                                       :to            now
                                       :granularity   "1-days"})))
              (is (= "exactly one dataset must be specified with accept header 'text/csv'"
                     (invalid-request {:accept-header "text/csv"
                                       :datasets      ["cpu-stats" "network-stats"]
                                       :from          (time/minus now (time/duration-unit 1 :days))
                                       :to            now
                                       :granularity   "1-days"})))
              (is (= "from parameter is mandatory, with format uuuu-MM-dd'T'HH:mm:ss[.SSS]XXXXX"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :granularity "1-days"})))
              (is (= "from parameter is mandatory, with format uuuu-MM-dd'T'HH:mm:ss[.SSS]XXXXX"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from-str    "wrong-datetime"
                                       :granularity "1-days"})))
              (is (= "to parameter is mandatory, with format uuuu-MM-dd'T'HH:mm:ss[.SSS]XXXXX"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :granularity "1-days"})))
              (is (= "to parameter is mandatory, with format uuuu-MM-dd'T'HH:mm:ss[.SSS]XXXXX"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to-str      "wrong-datetime"
                                       :granularity "1-days"})))
              (is (= "from must be before to"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        now
                                       :to          now
                                       :granularity "1-days"})))
              (is (= "unknown datasets: invalid-1,invalid-2"
                     (invalid-request {:datasets    ["invalid-1" "cpu-stats" "invalid-2"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to          now
                                       :granularity "1-days"})))
              (is (= "unrecognized value for granularity 1-invalid"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to          now
                                       :granularity "1-invalid"})))
              (is (= "too many data points requested. Please restrict the time interval or increase the time granularity."
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to          now
                                       :granularity "1-minutes"})))))

          (testing "cvs export of metrics data"
            (let [from        (time/minus now (time/duration-unit 1 :days))
                  to          now
                  csv-request (fn [dataset]
                                (-> (metrics-request {:accept-header "text/csv"
                                                      :datasets      [dataset]
                                                      :from          from
                                                      :to            to
                                                      :granularity   "1-days"})
                                    (ltu/is-status 200)
                                    (ltu/is-header "Content-Type" "text/csv")
                                    (ltu/is-header "Content-disposition" "attachment;filename=export.csv")
                                    (ltu/body)))]
              (is (= (str "nuvlaedge-id,timestamp,doc-count,avg-cpu-capacity,avg-cpu-load,avg-cpu-load-1,avg-cpu-load-5,context-switches,interrupts,software-interrupts,system-calls\n"
                          (str/join "," [nuvlabox-id
                                         (time/to-str (time/truncated-to-days now))
                                         1 10.0 5.5 nil nil nil nil nil nil]) "\n")
                     (csv-request "cpu-stats")))
              (is (= (str "nuvlaedge-id,timestamp,doc-count,avg-ram-capacity,avg-ram-used\n"
                          (str/join "," [nuvlabox-id
                                         (time/to-str (time/truncated-to-days now))
                                         1
                                         4096.0
                                         2000.0]) "\n")
                     (csv-request "ram-stats")))
              (is (= #{"nuvlaedge-id,disk.device,timestamp,doc-count,avg-disk-capacity,avg-disk-used"
                       (str/join "," [nuvlabox-id
                                      "root"
                                      (time/to-str (time/truncated-to-days now))
                                      1, 20000.0, 20000.0])
                       (str/join "," [nuvlabox-id
                                      "datastore"
                                      (time/to-str (time/truncated-to-days now))
                                      1
                                      20000.0
                                      15000.0])}
                     (set (str/split-lines (csv-request "disk-stats")))))
              (is (= #{"nuvlaedge-id,network.interface,timestamp,doc-count,bytes-received,bytes-transmitted"
                       (str/join "," [nuvlabox-id
                                      "eth0"
                                      (time/to-str (time/truncated-to-days now))
                                      1
                                      5579821.0
                                      44145.0])
                       (str/join "," [nuvlabox-id
                                      "vpn"
                                      (time/to-str (time/truncated-to-days now))
                                      1
                                      3019.0
                                      78.0])}
                     (set (str/split-lines (csv-request "network-stats")))))
              (is (= #{"nuvlaedge-id,power-consumption.metric-name,timestamp,doc-count,energy-consumption"
                       (str/join "," [nuvlabox-id
                                      "IN_current"
                                      (time/to-str (time/truncated-to-days now))
                                      1
                                      2.4])}
                     (set (str/split-lines (csv-request "power-consumption-stats")))))))))

      (testing "metrics data on multiple nuvlaboxes"
        (let [;; add another nuvlabox
              nuvlabox-id-2     (-> session-user
                                    (request nuvlabox-base-uri
                                             :request-method :post
                                             :body (json/write-str valid-nuvlabox))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/location))

              valid-acl-2       {:owners    ["group/nuvla-admin"]
                                 :edit-data [nuvlabox-id-2]}

              session-nb        (header session authn-info-header (str "user/jane user/jane group/nuvla-user group/nuvla-anon"))
              status-id-2       (-> session-admin
                                    (request base-uri
                                             :request-method :post
                                             :body (json/write-str (assoc valid-state :parent nuvlabox-id-2
                                                                                      :acl valid-acl-2)))
                                    (ltu/body->edn)
                                    (ltu/is-status 201)
                                    (ltu/body-resource-id))
              status-url-2      (str p/service-context status-id-2)
              ;; update the nuvlabox
              _                 (-> session-admin
                                    (request status-url-2
                                             :request-method :put
                                             :body (json/write-str {:current-time (time/now-str)
                                                                    :online       true
                                                                    :resources    resources-updated}))
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    ltu/body)
              nuvlabox-data-url (str p/service-context nuvlabox/resource-type "/data")
              now               (time/now)
              metrics-request   (fn [{:keys [datasets from from-str to to-str granularity accept-header]}]
                                  (-> session-nb
                                      (cond-> accept-header (header "accept" accept-header))
                                      (request nuvlabox-data-url
                                               :request-method :patch
                                               :headers {:bulk true}
                                               :body (json/write-str
                                                       {:filter      (str "(id='" nuvlabox-id "' or id='" nuvlabox-id-2 "')")
                                                        :dataset     datasets
                                                        :from        (if from (time/to-str from) from-str)
                                                        :to          (if to (time/to-str to) to-str)
                                                        :granularity granularity}))))]
          (testing "new metrics data is added to ts-nuvlaedge time-serie"
            (ltu/refresh-es-indices)
            (let [from        (time/minus (time/now) (time/duration-unit 1 :days))
                  to          now
                  metric-data (-> (metrics-request {:datasets    ["online-status-stats"
                                                                  "online-status-by-edge"
                                                                  "cpu-stats"
                                                                  "ram-stats"
                                                                  "disk-stats"
                                                                  "network-stats"
                                                                  "power-consumption-stats"]
                                                    :from        from
                                                    :to          to
                                                    :granularity "1-days"})
                                  (ltu/is-status 200)
                                  (ltu/body->edn)
                                  (ltu/body))]
              (is (= [{:dimensions {:nuvlaedge-count 2}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    2
                                     :aggregations {:avg-avg-online {:value 1.0}}}]}]
                     (:online-status-stats metric-data)))
              (is (= [{:dimensions {:nuvlaedge-count 2}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    2
                                     :aggregations {:avg-avg-online {:value 1.0}
                                                    :avg-online     {:buckets                     #{{:doc_count       1
                                                                                                     :edge-avg-online {:value 1.0}
                                                                                                     :key             nuvlabox-id}
                                                                                                    {:doc_count       1
                                                                                                     :edge-avg-online {:value 1.0}
                                                                                                     :key             nuvlabox-id-2}}
                                                                     :doc_count_error_upper_bound 0
                                                                     :sum_other_doc_count         0}}}]}]
                     (update-in (:online-status-by-edge metric-data) [0 :ts-data 0 :aggregations :avg-online :buckets] set)))
              (is (= [{:dimensions {:nuvlaedge-count 2}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    2
                                     :aggregations {:sum-avg-cpu-capacity    {:value 20.0}
                                                    :sum-avg-cpu-load        {:value 11.0}
                                                    :sum-avg-cpu-load-1      {:value 0.0}
                                                    :sum-avg-cpu-load-5      {:value 0.0}
                                                    :sum-context-switches    {:value 0.0}
                                                    :sum-interrupts          {:value 0.0}
                                                    :sum-software-interrupts {:value 0.0}
                                                    :sum-system-calls        {:value 0.0}}}]}]
                     (:cpu-stats metric-data)))
              (is (= [{:dimensions {:nuvlaedge-count 2}
                       :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                     :doc-count    2
                                     :aggregations {:sum-avg-ram-capacity {:value 8192.0}
                                                    :sum-avg-ram-used     {:value 4000.0}}}]}]
                     (:ram-stats metric-data)))
              (is (= #{{:dimensions {:nuvlaedge-count 2}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :aggregations {:sum-avg-disk-capacity {:value 80000.0}
                                                     :sum-avg-disk-used     {:value 70000.0}}
                                      :doc-count    4}]}}
                     (set (:disk-stats metric-data))))
              (is (= #{{:dimensions {:nuvlaedge-count 2}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :aggregations {:sum-bytes-received    {:value 1.116568E7}
                                                     :sum-bytes-transmitted {:value 88446.0}}
                                      :doc-count    4}]}}
                     (set (:network-stats metric-data))))
              (is (= #{{:dimensions {:nuvlaedge-count               2
                                     :power-consumption.metric-name "IN_current"}
                        :ts-data    [{:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    2
                                      :aggregations {:sum-energy-consumption {:value 4.8}
                                                     #_:unit                 #_"A"}}]}}
                     (set (:power-consumption-stats metric-data))))))

          (testing "query request validation"
            (let [invalid-request (fn [options]
                                    (-> (metrics-request options)
                                        (ltu/is-status 400)
                                        (ltu/body->edn)
                                        (ltu/body)
                                        :message))]
              (is (= "format not supported: text/plain"
                     (invalid-request {:accept-header "text/plain"
                                       :datasets      ["cpu-stats"]
                                       :from          (time/minus now (time/duration-unit 1 :days))
                                       :to            now
                                       :granularity   "1-days"})))
              (is (= "exactly one dataset must be specified with accept header 'text/csv'"
                     (invalid-request {:accept-header "text/csv"
                                       :datasets      ["cpu-stats" "network-stats"]
                                       :from          (time/minus now (time/duration-unit 1 :days))
                                       :to            now
                                       :granularity   "1-days"})))
              (is (= "from must be before to"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        now
                                       :to          now
                                       :granularity "1-days"})))
              (is (= "unknown datasets: invalid-1,invalid-2"
                     (invalid-request {:datasets    ["invalid-1" "cpu-stats" "invalid-2"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to          now
                                       :granularity "1-days"})))
              (is (= "unrecognized value for granularity 1-invalid"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to          now
                                       :granularity "1-invalid"})))
              (is (= "too many data points requested. Please restrict the time interval or increase the time granularity."
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :to          now
                                       :granularity "1-minutes"})))))

          (testing "cvs export of metrics data"
            (let [from        (time/minus now (time/duration-unit 1 :days))
                  to          now
                  csv-request (fn [dataset]
                                (-> (metrics-request {:accept-header "text/csv"
                                                      :datasets      [dataset]
                                                      :from          from
                                                      :to            to
                                                      :granularity   "1-days"})
                                    (ltu/is-status 200)
                                    (ltu/is-header "Content-Type" "text/csv")
                                    (ltu/is-header "Content-disposition" "attachment;filename=export.csv")
                                    (ltu/body)))]
              (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-avg-cpu-capacity,sum-avg-cpu-load,sum-avg-cpu-load-1,sum-avg-cpu-load-5,sum-context-switches,sum-interrupts,sum-software-interrupts,sum-system-calls\n"
                          (str/join "," [2
                                         (time/to-str (time/truncated-to-days now))
                                         2, 20.0, 11.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]) "\n")
                     (csv-request "cpu-stats")))
              (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-avg-ram-capacity,sum-avg-ram-used\n"
                          (str/join "," [2
                                         (time/to-str (time/truncated-to-days now))
                                         2
                                         8192.0
                                         4000.0]) "\n")
                     (csv-request "ram-stats")))
              (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-avg-disk-capacity,sum-avg-disk-used\n"
                          (str/join "," [2
                                         (time/to-str (time/truncated-to-days now))
                                         4, 80000.0, 70000.0]) "\n")
                     (csv-request "disk-stats")))
              (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-bytes-received,sum-bytes-transmitted\n"
                          (str/join "," [2
                                         (time/to-str (time/truncated-to-days now))
                                         4
                                         1.116568E7
                                         88446.0]) "\n")
                     (csv-request "network-stats")))
              (is (= (str "nuvlaedge-count,power-consumption.metric-name,timestamp,doc-count,sum-energy-consumption\n"
                          (str/join "," [2
                                         "IN_current"
                                         (time/to-str (time/truncated-to-days now))
                                         2
                                         4.8]) "\n")
                     (csv-request "power-consumption-stats"))))))))))


(deftest lifecycle-online-next-heartbeat
  (test-online-next-heartbeat))

(deftest lifecycle-swarm-enabled
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

      (-> session-nb
          (request (-> session-nb
                       (request nuvlabox-url)
                       (ltu/body->edn)
                       (ltu/is-status 200)
                       (ltu/get-op-url :commission))
                   :request-method :put
                   :body (json/write-str {:swarm-endpoint "https://swarm.example.com"}))
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [nuvlabox    (-> session-nb
                            (request nuvlabox-url)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-key-value some? :nuvlabox-status true)
                            (ltu/is-key-value some? :infrastructure-service-group true)
                            ltu/body)
            status-url  (str p/service-context (:nuvlabox-status nuvlabox))
            srv-grp-id  (:infrastructure-service-group nuvlabox)
            service-url (str p/service-context (nb-utils/get-service "swarm" srv-grp-id))]
        (-> session-nb
            (request service-url)
            (ltu/body->edn)
            (ltu/is-key-value :swarm-enabled false)
            (ltu/is-key-value :swarm-manager nil))

        (-> session-nb
            (request status-url
                     :request-method :put
                     :body (json/write-str {:orchestrator "swarm"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (ltu/refresh-es-indices)
        (-> session-nb
            (request service-url)
            (ltu/body->edn)
            (ltu/is-key-value :swarm-enabled true)
            (ltu/is-key-value :swarm-manager false))

        (-> session-nb
            (request status-url
                     :request-method :put
                     :body (json/write-str {}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (ltu/refresh-es-indices)
        (-> session-nb
            (request service-url)
            (ltu/body->edn)
            (ltu/is-key-value :swarm-enabled true)
            (ltu/is-key-value :swarm-manager false))

        (-> session-nb
            (request status-url
                     :request-method :put
                     :body (json/write-str {:cluster-node-role "manager"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (ltu/refresh-es-indices)
        (-> session-nb
            (request service-url)
            (ltu/body->edn)
            (ltu/is-key-value :swarm-enabled true)
            (ltu/is-key-value :swarm-manager true))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-status/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))


