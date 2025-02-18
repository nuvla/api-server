(ns com.sixsq.nuvla.server.resources.nuvlabox-status-2-lifecycle-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [com.sixsq.nuvla.server.resources.nuvlabox-status-2 :as nb-status-2]
    [com.sixsq.nuvla.server.resources.nuvlabox.data-utils :as data-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.resources.ts-nuvlaedge-availability :as ts-ne-availability]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [com.sixsq.nuvla.server.util.time :as time]
    [com.sixsq.nuvla.utils.log-time :as logt]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [same.compare :refer [compare-ulp]]
    [same.core :refer [ish?]])
  (:import (java.text DecimalFormat DecimalFormatSymbols)
           (java.util Locale)))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-status/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def timestamp (time/now-str))


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")


(def nb-name "nb-test-status")

(def valid-nuvlabox {:name  nb-name
                     :owner nuvlabox-owner})

(def nb-name2 "nb-test-status2")

(def valid-nuvlabox2 {:name  nb-name2
                      :owner nuvlabox-owner})

(def nb-name3 "nb-test-status3")

(def valid-nuvlabox3 {:name  nb-name3
                      :owner nuvlabox-owner})

(def nb-name4 "nb-test-status4")

(def valid-nuvlabox4 {:name  nb-name4
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

(deftest lifecycle-json-patch-edit
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

          nuvlabox-id   (-> session-user
                            (request nuvlabox-base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          valid-acl     {:owners    ["group/nuvla-admin"]
                         :edit-data [nuvlabox-id]}

          session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

      (when-let [status-url (-> session-admin
                                (request base-uri
                                         :request-method :post
                                         :body (j/write-value-as-string (assoc (select-keys valid-state [:version :status]) :parent nuvlabox-id
                                                                                                                   :acl valid-acl
                                                                                                                   :coe-resources {:docker {:containers []}})))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location-url))]
        (-> session-nb
            (request status-url
                     :content-type "application/json-patch+json"
                     :request-method :put
                     :body "[{\"op\": \"add\", \"path\": \"/coe-resources\", \"value\": {\"docker\": {\"containers\": [{\"id\": 1, \"labels\": {\"a\": \"1\", \"a/b/c\": \"2\"}}] }}}]")
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request status-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value u/stringify-keys :coe-resources {"docker" {"containers" [{"id"     1
                                                                                        "labels" {"a"     "1"
                                                                                                  "a/b/c" "2"}}]}}))))))

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
                                     :body (j/write-value-as-string valid-nuvlabox))
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
                       :body (j/write-value-as-string (assoc valid-state :parent nuvlabox-id
                                                                :acl valid-acl)))
              (ltu/body->edn)
              (ltu/is-status 403))))

      (when-let [status-id (-> session-admin
                               (request base-uri
                                        :request-method :post
                                        :body (j/write-value-as-string (assoc valid-state :parent nuvlabox-id
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
                                    :body (j/write-value-as-string {:current-time (time/now-str)
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

          (let [new-tags ["foo"]]
            (testing "nuvlabox user is able to patch update nuvlabox-status"
              (-> session-nb
                  (request status-url
                           :content-type "application/json-patch+json"
                           :request-method :put
                           :body (j/write-value-as-string [{"op" "add" "path" "/tags" "value" new-tags}
                                                  {"op" "test" "path" "/tags" "value" new-tags}]))
                  (ltu/body->edn)
                  (ltu/is-status 200))
              (testing "spec error are returned to the user"
                (-> session-nb
                    (request status-url
                             :content-type "application/json-patch+json"
                             :request-method :put
                             :body (j/write-value-as-string [{"op" "add" "path" "/description" "value" 1}]))
                    (ltu/body->edn)
                    (ltu/is-status 400)))
              (testing "patch error are returned to the user"
                (-> session-nb
                    (request status-url
                             :content-type "application/json-patch+json"
                             :request-method :put
                             :body (j/write-value-as-string [{"op" "remove" "path" "/wrong/1" "value" "x"}]))
                    (ltu/body->edn)
                    (ltu/is-status 400)
                    (ltu/message-matches "Json patch exception: no such path in target JSON document"))
                (-> session-nb
                    (request status-url
                             :content-type "application/json-patch+json"
                             :request-method :put
                             :body (j/write-value-as-string "plain text"))
                    (ltu/body->edn)
                    (ltu/is-status 400)
                    (ltu/message-matches "Json patch exception: Cannot deserialize value of type")))))

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
                                     :body (j/write-value-as-string valid-nuvlabox))
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
                     :body (j/write-value-as-string
                             {:capabilities [nb-utils/capability-job-pull]}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (testing "admin edition doesn't set online flag"
          (let [called (atom false)]
            (with-redefs [ts-ne-availability/add-impl (fn [& _] (reset! called true))]
              (-> session-admin
                  (request status-url
                           :request-method :put
                           :body (j/write-value-as-string {}))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :online nil)
                  (ltu/is-key-value :last-heartbeat nil)
                  (ltu/is-key-value :next-heartbeat nil)))
            (is (false? @called) "availability metric was inserted, but it should not have")))

        (-> session-admin
            (request status-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        (testing "nuvlabox can do a legacy heartbeat"
          (let [called (atom false)]
            (with-redefs [ts-ne-availability/add-impl (fn [& _] (reset! called true))]
              (-> session-nb
                  (request status-url
                           :request-method :put
                           :body (j/write-value-as-string {:nuvlabox-engine-version "1.0.2"}))
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
            (is (true? @called) "availability metric was not inserted, but it should have"))

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
                       :body (j/write-value-as-string {:nuvlabox-engine-version "1.0.2"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value count :jobs 1)))

        (testing "admin can set offline"
          (let [called (atom false)]
            (with-redefs [ts-ne-availability/add-impl (fn [& _] (reset! called true))]
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
              (is (true? @called) "availability metric was not inserted, but it should have"))))

        (testing "when a nuvlabox send telemetry that has a spec validation
          issue that can't be fixed, the heartbeat is still updated"
          (-> session-nb
              (request status-url
                       :request-method :put
                       :body (j/write-value-as-string {:description 1}))
              (ltu/body->edn)
              (ltu/is-status 400))

          (-> session-nb
              (request status-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true)
              (ltu/is-key-value :description "NuvlaEdge status of nb-test-status")
              (ltu/is-key-value string? :last-heartbeat true)
              (ltu/is-key-value string? :next-heartbeat true)))

        (-> session-admin
            (request (-> session-admin
                         (request nuvlabox-url)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/get-op-url nb-utils/action-set-offline)))
            (ltu/body->edn)
            (ltu/is-status 200))

        (testing "when a nuvlabox send telemetry that has a spec validation
          issue that can be fixed, the heartbeat is still updated and valid values are updated"
          (-> session-nb
              (request status-url
                       :request-method :put
                       :body (j/write-value-as-string {:description "hello"
                                              :wrong       1}))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-nb
              (request status-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true)
              (ltu/is-key-value :description "hello")))))))

(defn check-accept-header
  [metrics-request now]
  (testing "accept header"
    (let [invalid-format (fn [accept-header]
                           (-> (metrics-request
                                 {:accept-header accept-header
                                  :datasets      ["cpu-stats"]
                                  :from          (time/minus now (time/duration-unit 1 :days))
                                  :to            now
                                  :granularity   "1-days"})
                               (ltu/is-status 406)
                               (ltu/body->edn)
                               (ltu/is-key-value :message "Not Acceptable")))]
      (invalid-format "text/plain")
      (invalid-format "text/html"))
    (let [metrics-request (fn [accept-header response-content-type]
                            (-> (metrics-request
                                  (cond->
                                    {:datasets    ["cpu-stats"]
                                     :from        (time/minus now (time/duration-unit 1 :days))
                                     :to          now
                                     :granularity "1-days"}
                                    accept-header
                                    (assoc :accept-header accept-header)))
                                (ltu/is-status 200)
                                (ltu/is-header "Content-Type" response-content-type)))]
      (metrics-request nil
                       "application/json")
      (metrics-request "application/json"
                       "application/json")
      (metrics-request "application/*"
                       "application/json")
      (metrics-request "*/*"
                       "application/json")
      (metrics-request "application/json;q=1.0,text/csv;q=0.1"
                       "application/json")
      (metrics-request "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                       "application/json")
      (metrics-request "text/csv"
                       "text/csv")
      (metrics-request "text/*"
                       "text/csv")
      (metrics-request "application/json;q=0.1,text/csv;q=1.0"
                       "text/csv"))))

(deftest telemetry-data
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session         (-> (ltu/ring-app)
                              session
                              (content-type "application/json"))
          session-admin   (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user    (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
          create-nuvlabox (fn [body]
                            (let [nuvlabox     (-> session-user
                                                   (request nuvlabox-base-uri
                                                            :request-method :post
                                                            :body (j/write-value-as-string body))
                                                   (ltu/body->edn)
                                                   (ltu/is-status 201))
                                  nuvlabox-id  (ltu/location nuvlabox)
                                  nuvlabox-url (str p/service-context nuvlabox-id)
                                  _            (-> session-user
                                                   (request (-> session-user
                                                                (request nuvlabox-url)
                                                                (ltu/body->edn)
                                                                (ltu/is-status 200)
                                                                (ltu/get-op-url :activate)))
                                                   (ltu/body->edn)
                                                   (ltu/is-status 200))
                                  _            (-> session-user
                                                   (request (-> session-user
                                                                (request nuvlabox-url)
                                                                (ltu/body->edn)
                                                                (ltu/is-status 200)
                                                                (ltu/get-op-url :commission)))
                                                   (ltu/body->edn)
                                                   (ltu/is-status 200))]
                              nuvlabox-id))
          nuvlabox-id     (create-nuvlabox valid-nuvlabox)
          nuvlabox-url    (str p/service-context nuvlabox-id)

          valid-acl       {:owners    ["group/nuvla-admin"]
                           :edit-data [nuvlabox-id]}

          session-nb      (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))
          status-id       (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string (assoc valid-state :parent nuvlabox-id
                                                                                :acl valid-acl)))
                              (ltu/body->edn)
                              (ltu/is-status 201)
                              (ltu/body-resource-id))
          status-url      (str p/service-context
                               status-id)
          update-time     (time/now)]

      ;; update the nuvlabox
      (-> session-nb
          (request status-url
                   :request-method :put
                   :body (j/write-value-as-string {:current-time (time/to-str update-time)
                                          :online       true
                                          :resources    resources-updated}))
          (ltu/body->edn)
          (ltu/is-status 200)
          ltu/body)
      (ltu/refresh-es-indices)

      (testing "metrics data on a single nuvlabox"
        (let [nuvlabox-data-url  (str nuvlabox-url "/data")
              now                (time/now)
              midnight-today     (time/truncated-to-days now)
              midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
              metrics-request    (fn [{:keys [datasets from from-str to to-str granularity custom-es-aggregations accept-header] #_:or #_{accept-header "application/json"}}]
                                   (ltu/refresh-es-indices)
                                   (-> session-nb
                                       (content-type "application/x-www-form-urlencoded")
                                       (cond-> accept-header (header "accept" accept-header))
                                       (request nuvlabox-data-url
                                                :body (rc/form-encode
                                                        (cond->
                                                          {:dataset datasets
                                                           :from    (if from (time/to-str from) from-str)
                                                           :to      (if to (time/to-str to) to-str)}
                                                          granularity (assoc :granularity granularity)
                                                          custom-es-aggregations (assoc :custom-es-aggregations custom-es-aggregations))))))]
          (testing "new metrics data is added to ts-nuvlaedge time-serie"
            (let [from        (time/minus (time/now) (time/duration-unit 1 :days))
                  to          now
                  metric-data (-> (metrics-request {:datasets    ["cpu-stats"
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
                       :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                     :doc-count    0
                                     :aggregations {:avg-cpu-capacity    {:value nil}
                                                    :avg-cpu-load        {:value nil}
                                                    :avg-cpu-load-1      {:value nil}
                                                    :avg-cpu-load-5      {:value nil}
                                                    :context-switches    {:value nil}
                                                    :interrupts          {:value nil}
                                                    :software-interrupts {:value nil}
                                                    :system-calls        {:value nil}}}
                                    {:timestamp    (time/to-str midnight-today)
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
                       :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                     :doc-count    0
                                     :aggregations {:avg-ram-capacity {:value nil}
                                                    :avg-ram-used     {:value nil}}}
                                    {:timestamp    (time/to-str midnight-today)
                                     :doc-count    1
                                     :aggregations {:avg-ram-capacity {:value 4096.0}
                                                    :avg-ram-used     {:value 2000.0}}}]}]
                     (:ram-stats metric-data)))
              (is (= #{{:dimensions {:nuvlaedge-id nuvlabox-id
                                     :disk.device  "root"}
                        :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                      :doc-count    0
                                      :aggregations {:avg-disk-capacity {:value nil}
                                                     :avg-disk-used     {:value nil}}}
                                     {:timestamp    (time/to-str midnight-today)
                                      :doc-count    1
                                      :aggregations {:avg-disk-capacity {:value 20000.0}
                                                     :avg-disk-used     {:value 20000.0}}}]}
                       {:dimensions {:nuvlaedge-id nuvlabox-id
                                     :disk.device  "datastore"}
                        :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                      :doc-count    0
                                      :aggregations {:avg-disk-capacity {:value nil}
                                                     :avg-disk-used     {:value nil}}}
                                     {:timestamp    (time/to-str (time/truncated-to-days now))
                                      :doc-count    1
                                      :aggregations {:avg-disk-capacity {:value 20000.0}
                                                     :avg-disk-used     {:value 15000.0}}}]}}
                     (set (:disk-stats metric-data))))
              (is (= #{{:dimensions {:nuvlaedge-id      nuvlabox-id
                                     :network.interface "eth0"}
                        :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                      :doc-count    0
                                      :aggregations {:bytes-received    {:value nil}
                                                     :bytes-transmitted {:value nil}}}
                                     {:timestamp    (time/to-str midnight-today)
                                      :doc-count    1
                                      :aggregations {:bytes-received    {:value 5579821.0}
                                                     :bytes-transmitted {:value 44145.0}}}]}
                       {:dimensions {:nuvlaedge-id      nuvlabox-id
                                     :network.interface "vpn"}
                        :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                      :doc-count    0
                                      :aggregations {:bytes-received    {:value nil}
                                                     :bytes-transmitted {:value nil}}}
                                     {:timestamp    (time/to-str midnight-today)
                                      :doc-count    1
                                      :aggregations {:bytes-received    {:value 3019.0}
                                                     :bytes-transmitted {:value 78.0}}}]}}
                     (set (:network-stats metric-data))))
              (is (= #{{:dimensions {:nuvlaedge-id                  nuvlabox-id
                                     :power-consumption.metric-name "IN_current"}
                        :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                      :doc-count    0
                                      :aggregations {:energy-consumption {:value nil}
                                                     #_:unit                 #_"A"}}
                                     {:timestamp    (time/to-str midnight-today)
                                      :doc-count    1
                                      :aggregations {:energy-consumption {:value 2.4}
                                                     #_:unit                 #_"A"}}]}}
                     (set (:power-consumption-stats metric-data))))))

          (testing "raw metric data query"
            (let [from            (time/minus (time/now) (time/duration-unit 1 :days))
                  to              now
                  raw-metric-data (-> (metrics-request {:datasets    ["cpu-stats"
                                                                      "ram-stats"
                                                                      "disk-stats"
                                                                      "network-stats"
                                                                      "power-consumption-stats"]
                                                        :from        from
                                                        :to          to
                                                        :granularity "raw"})
                                      (ltu/is-status 200)
                                      (ltu/body->edn)
                                      (ltu/body))]
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    [{:cpu          {:capacity 10
                                                    :load     5.5}
                                     :metric       "cpu"
                                     :nuvlaedge-id nuvlabox-id
                                     :timestamp    (time/to-str update-time)}]}]
                     (:cpu-stats raw-metric-data)))
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    [{:metric       "ram"
                                     :nuvlaedge-id nuvlabox-id
                                     :ram          {:capacity 4096
                                                    :used     2000}
                                     :timestamp    (time/to-str update-time)}]}]
                     (:ram-stats raw-metric-data)))
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    #{{:disk         {:capacity 20000
                                                     :device   "root"
                                                     :used     20000}
                                      :metric       "disk"
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:disk         {:capacity 20000
                                                     :device   "datastore"
                                                     :used     15000}
                                      :metric       "disk"
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}}}]
                     (update-in (:disk-stats raw-metric-data) [0 :ts-data] set)))
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    #{{:metric       "network"
                                      :network      {:bytes-received    3019.0
                                                     :bytes-transmitted 78.0
                                                     :interface         "vpn"}
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:metric       "network"
                                      :network      {:bytes-received    5579821.0
                                                     :bytes-transmitted 44145.0
                                                     :interface         "eth0"}
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}}}]
                     (update-in (:network-stats raw-metric-data) [0 :ts-data] set)))
              (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                       :ts-data    [{:metric            "power-consumption"
                                     :nuvlaedge-id      nuvlabox-id
                                     :power-consumption {:energy-consumption 2.4
                                                         :metric-name        "IN_current"
                                                         :unit               "A"}
                                     :timestamp         (time/to-str update-time)}]}]
                     (:power-consumption-stats raw-metric-data)))))

          (testing "custom es aggregations metric data query"
            (let [from (time/minus (time/now) (time/duration-unit 1 :days))
                  to   now]
              (testing "custom aggregation on cpu-stats"
                (let [custom-cpu-agg (-> (metrics-request
                                           {:datasets               ["cpu-stats"]
                                            :from                   from
                                            :to                     to
                                            :custom-es-aggregations (j/write-value-as-string
                                                                      {:agg1 {:date_histogram
                                                                              {:field          "@timestamp"
                                                                               :fixed_interval "1d"
                                                                               :min_doc_count  0}
                                                                              :aggregations {:avg-cpu-load {:avg {:field :cpu.load}}
                                                                                             :min-cpu-load {:min {:field :cpu.load}}
                                                                                             :max-cpu-load {:max {:field :cpu.load}}}}})})
                                         (ltu/is-status 200)
                                         (ltu/body->edn)
                                         (ltu/body))]
                  (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                           :agg1       [{:aggregations {:avg-cpu-load {:value 5.5}
                                                        :max-cpu-load {:value 5.5}
                                                        :min-cpu-load {:value 5.5}}
                                         :doc-count    1
                                         :timestamp    (time/to-str midnight-today)}]}]
                         (:cpu-stats custom-cpu-agg)))))
              (testing "custom aggregation on disk-stats"
                (let [custom-cpu-agg (-> (metrics-request
                                           {:datasets               ["disk-stats"]
                                            :from                   from
                                            :to                     to
                                            :custom-es-aggregations (j/write-value-as-string
                                                                      {:agg1 {:date_histogram
                                                                              {:field          "@timestamp"
                                                                               :fixed_interval "1d"
                                                                               :min_doc_count  0}
                                                                              :aggregations {:total-disk-capacity {:sum {:field :disk.capacity}}
                                                                                             :total-disk-used     {:sum {:field :disk.used}}}}})})
                                         (ltu/is-status 200)
                                         (ltu/body->edn)
                                         (ltu/body))]
                  (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                           :agg1       [{:aggregations {:total-disk-capacity {:value 40000.0}
                                                        :total-disk-used     {:value 35000.0}}
                                         :doc-count    2
                                         :timestamp    (time/to-str midnight-today)}]}]
                         (:disk-stats custom-cpu-agg)))))))

          (check-accept-header metrics-request now)

          (testing "query request validation"
            (let [invalid-request (fn [options]
                                    (-> (metrics-request options)
                                        (ltu/is-status 400)
                                        (ltu/body->edn)
                                        (ltu/body)
                                        :message))]
              (is (= "exactly one dataset must be specified with accept header 'text/csv'"
                     (invalid-request {:accept-header "text/csv"
                                       :datasets      ["cpu-stats" "network-stats"]
                                       :from          (time/minus now (time/duration-unit 1 :days))
                                       :to            now
                                       :granularity   "1-days"})))
              (is (= "from parameter is mandatory, with format iso8601 (uuuu-MM-dd'T'HH:mm:ss[.SSS]Z)"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :granularity "1-days"})))
              (is (= "from parameter is mandatory, with format iso8601 (uuuu-MM-dd'T'HH:mm:ss[.SSS]Z)"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from-str    "wrong-datetime"
                                       :granularity "1-days"})))
              (is (= "to parameter is mandatory, with format iso8601 (uuuu-MM-dd'T'HH:mm:ss[.SSS]Z)"
                     (invalid-request {:datasets    ["cpu-stats"]
                                       :from        (time/minus now (time/duration-unit 1 :days))
                                       :granularity "1-days"})))
              (is (= "to parameter is mandatory, with format iso8601 (uuuu-MM-dd'T'HH:mm:ss[.SSS]Z)"
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

          (testing "csv export of metrics data"
            (let [from        (time/minus now (time/duration-unit 1 :days))
                  to          now
                  csv-request (fn [dataset granularity]
                                (-> (metrics-request {:accept-header "text/csv"
                                                      :datasets      [dataset]
                                                      :from          from
                                                      :to            to
                                                      :granularity   granularity})
                                    (ltu/is-status 200)
                                    (ltu/is-header "Content-Type" "text/csv")
                                    (ltu/is-header "Content-disposition" "attachment;filename=export.csv")
                                    (ltu/body)))]
              (testing "Export with predefined aggregations"
                (is (= (str "timestamp,doc-count,avg-cpu-capacity,avg-cpu-load,avg-cpu-load-1,avg-cpu-load-5,context-switches,interrupts,software-interrupts,system-calls\n"
                            (str/join "," [(time/to-str midnight-yesterday)
                                           0 nil nil nil nil nil nil nil nil]) "\n"
                            (str/join "," [(time/to-str midnight-today)
                                           1 10 5.5 nil nil nil nil nil nil]) "\n")
                       (csv-request "cpu-stats" "1-days")))
                (is (= (str "timestamp,doc-count,avg-ram-capacity,avg-ram-used\n"
                            (str/join "," [(time/to-str midnight-yesterday)
                                           0 nil nil]) "\n"
                            (str/join "," [(time/to-str midnight-today)
                                           1 4096 2000]) "\n")
                       (csv-request "ram-stats" "1-days")))
                (is (= #{"disk.device,timestamp,doc-count,avg-disk-capacity,avg-disk-used"
                         (str/join "," ["root"
                                        (time/to-str midnight-yesterday)
                                        0, nil, nil])
                         (str/join "," ["root"
                                        (time/to-str midnight-today)
                                        1, 20000, 20000])
                         (str/join "," ["datastore"
                                        (time/to-str midnight-yesterday)
                                        0, nil, nil])
                         (str/join "," ["datastore"
                                        (time/to-str midnight-today)
                                        1 20000 15000])}
                       (set (str/split-lines (csv-request "disk-stats" "1-days")))))
                (is (= #{"network.interface,timestamp,doc-count,bytes-received,bytes-transmitted"
                         (str/join "," ["eth0"
                                        (time/to-str midnight-yesterday)
                                        0 nil nil])
                         (str/join "," ["eth0"
                                        (time/to-str midnight-today)
                                        1 5579821 44145])
                         (str/join "," ["vpn"
                                        (time/to-str midnight-yesterday)
                                        0 nil nil])
                         (str/join "," ["vpn"
                                        (time/to-str midnight-today)
                                        1 3019 78])}
                       (set (str/split-lines (csv-request "network-stats" "1-days")))))
                (is (= #{"power-consumption.metric-name,timestamp,doc-count,energy-consumption"
                         (str/join "," ["IN_current"
                                        (time/to-str midnight-yesterday)
                                        0 nil])
                         (str/join "," ["IN_current"
                                        (time/to-str midnight-today)
                                        1
                                        2.4])}
                       (-> (csv-request "power-consumption-stats" "1-days")
                           str/split-lines
                           set))))
              (testing "Export raw telemetry data"
                (is (= (str "timestamp,capacity,load\n"
                            (str/join "," [(time/to-str update-time)
                                           10 5.5]) "\n")
                       (csv-request "cpu-stats" "raw")))
                (is (= (str "timestamp,capacity,used\n"
                            (str/join "," [(time/to-str update-time)
                                           4096 2000]) "\n")
                       (csv-request "ram-stats" "raw")))
                (is (= #{"timestamp,capacity,device,used"
                         (str/join "," [(time/to-str update-time)
                                        20000 "root" 20000])
                         (str/join "," [(time/to-str update-time)
                                        20000 "datastore" 15000])}
                       (-> (csv-request "disk-stats" "raw")
                           str/split-lines
                           set)))
                (is (= #{"timestamp,bytes-received,bytes-transmitted,interface"
                         (str/join "," [(time/to-str update-time)
                                        3019 78 "vpn"])
                         (str/join "," [(time/to-str update-time)
                                        5579821 44145 "eth0"])}
                       (-> (csv-request "network-stats" "raw")
                           str/split-lines
                           set)))
                (is (= (str "timestamp,energy-consumption,metric-name,unit\n"
                            (str/join "," [(time/to-str update-time)
                                           2.4 "IN_current" "A"]) "\n")
                       (csv-request "power-consumption-stats" "raw"))))
              (testing "Export with custom es aggregations not allowed"
                (let [csv-custom-cpu-agg (-> (metrics-request
                                               {:accept-header          "text/csv"
                                                :datasets               ["cpu-stats"]
                                                :from                   from
                                                :to                     to
                                                :custom-es-aggregations (j/write-value-as-string
                                                                          {:agg1 {:date_histogram
                                                                                  {:field          "@timestamp"
                                                                                   :fixed_interval "1d"
                                                                                   :min_doc_count  0}
                                                                                  :aggregations {:avg-cpu-load {:avg {:field :cpu.load}}
                                                                                                 :min-cpu-load {:min {:field :cpu.load}}
                                                                                                 :max-cpu-load {:max {:field :cpu.load}}}}})})
                                             (ltu/is-status 400)
                                             (ltu/body->edn)
                                             (ltu/body))]
                  (is (= "Custom aggregations cannot be exported to csv format"
                         (:message csv-custom-cpu-agg)))))))))

      (testing "metrics data on multiple nuvlaboxes"
        (let [;; add another nuvlabox
              nuvlabox-id-2      (create-nuvlabox valid-nuvlabox2)
              valid-acl-2        {:owners    ["group/nuvla-admin"]
                                  :edit-data [nuvlabox-id-2]}

              session-nb         (header session authn-info-header (str "user/jane user/jane group/nuvla-user group/nuvla-anon"))
              status-id-2        (-> session-admin
                                     (request base-uri
                                              :request-method :post
                                              :body (j/write-value-as-string (assoc valid-state :parent nuvlabox-id-2
                                                                                       :acl valid-acl-2)))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/body-resource-id))
              status-url-2       (str p/service-context status-id-2)
              ;; update the nuvlabox
              update-time-2      (time/now)
              _                  (-> session-admin
                                     (request status-url-2
                                              :request-method :put
                                              :body (j/write-value-as-string {:current-time (time/to-str update-time-2)
                                                                     :online       true
                                                                     :resources    resources-updated}))
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     ltu/body)
              ;; add yet another nuvlabox in state COMMISSIONED, but for which we send no metrics
              nuvlabox-id-3      (create-nuvlabox valid-nuvlabox3)
              ;; and another one which we leave in state NEW, so it should not be considered as offline
              nuvlabox-id-4      (-> session-user
                                     (request nuvlabox-base-uri
                                              :request-method :post
                                              :body (j/write-value-as-string valid-nuvlabox))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/location))
              nuvlabox-data-url  (str p/service-context nb/resource-type "/data")
              now                (time/now)
              midnight-today     (time/truncated-to-days now)
              midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
              metrics-request    (fn [{:keys [datasets from from-str to to-str granularity custom-es-aggregations accept-header]}]
                                   (ltu/refresh-es-indices)
                                   (-> session-nb
                                       (cond-> accept-header (header "accept" accept-header))
                                       (request nuvlabox-data-url
                                                :request-method :patch
                                                :headers {:bulk true}
                                                :body (j/write-value-as-string
                                                        (cond->
                                                          {:filter  (str "(id='" nuvlabox-id "'"
                                                                         " or id='" nuvlabox-id-2 "'"
                                                                         " or id='" nuvlabox-id-3 "'"
                                                                         " or id='" nuvlabox-id-4 "')")
                                                           :dataset datasets
                                                           :from    (if from (time/to-str from) from-str)
                                                           :to      (if to (time/to-str to) to-str)}
                                                          granularity (assoc :granularity granularity)
                                                          custom-es-aggregations (assoc :custom-es-aggregations custom-es-aggregations))))))]
          (testing "new metrics data is added to ts-nuvlaedge time-serie"
            (let [from        (time/minus (time/now) (time/duration-unit 1 :days))
                  to          now
                  metric-data (-> (metrics-request {:datasets    ["cpu-stats"
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
              (is (ish? [{:dimensions {:nuvlaedge-count 4}
                          :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                        :doc-count    0
                                        :aggregations {:sum-avg-cpu-capacity    {:value 0.0}
                                                       :sum-avg-cpu-load        {:value 0.0}
                                                       :sum-avg-cpu-load-1      {:value 0.0}
                                                       :sum-avg-cpu-load-5      {:value 0.0}
                                                       :sum-context-switches    {:value 0.0}
                                                       :sum-interrupts          {:value 0.0}
                                                       :sum-software-interrupts {:value 0.0}
                                                       :sum-system-calls        {:value 0.0}}}
                                       {:timestamp    (time/to-str midnight-today)
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
              (is (ish? [{:dimensions {:nuvlaedge-count 4}
                          :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                        :doc-count    0
                                        :aggregations {:sum-avg-ram-capacity {:value 0.0}
                                                       :sum-avg-ram-used     {:value 0.0}}}
                                       {:timestamp    (time/to-str midnight-today)
                                        :doc-count    2
                                        :aggregations {:sum-avg-ram-capacity {:value 8192.0}
                                                       :sum-avg-ram-used     {:value 4000.0}}}]}]
                        (:ram-stats metric-data)))
              (is (ish? #{{:dimensions {:nuvlaedge-count 4}
                           :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                         :aggregations {:sum-avg-disk-capacity {:value 0.0}
                                                        :sum-avg-disk-used     {:value 0.0}}
                                         :doc-count    0}
                                        {:timestamp    (time/to-str midnight-today)
                                         :aggregations {:sum-avg-disk-capacity {:value 80000.0}
                                                        :sum-avg-disk-used     {:value 70000.0}}
                                         :doc-count    4}]}}
                        (set (:disk-stats metric-data))))
              (is (ish? #{{:dimensions {:nuvlaedge-count 4}
                           :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                         :aggregations {:sum-bytes-received    {:value 0.0}
                                                        :sum-bytes-transmitted {:value 0.0}}
                                         :doc-count    0}
                                        {:timestamp    (time/to-str midnight-today)
                                         :aggregations {:sum-bytes-received    {:value 1.116568E7}
                                                        :sum-bytes-transmitted {:value 88446.0}}
                                         :doc-count    4}]}}
                        (set (:network-stats metric-data))))
              (is (ish? #{{:dimensions {:nuvlaedge-count               4
                                        :power-consumption.metric-name "IN_current"}
                           :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                         :doc-count    0
                                         :aggregations {:sum-energy-consumption {:value 0.0}
                                                        #_:unit                 #_"A"}}
                                        {:timestamp    (time/to-str midnight-today)
                                         :doc-count    2
                                         :aggregations {:sum-energy-consumption {:value 4.8}
                                                        #_:unit                 #_"A"}}]}}
                        (set (:power-consumption-stats metric-data))))))
          (testing "raw metrics data query"
            (let [from            (time/minus (time/now) (time/duration-unit 1 :days))
                  to              now
                  raw-metric-data (-> (metrics-request {:datasets    ["cpu-stats"
                                                                      "ram-stats"
                                                                      "disk-stats"
                                                                      "network-stats"
                                                                      "power-consumption-stats"]
                                                        :from        from
                                                        :to          to
                                                        :granularity "raw"})
                                      (ltu/is-status 200)
                                      (ltu/body->edn)
                                      (ltu/body))]
              (is (= [{:dimensions {:nuvlaedge-count 4}
                       :ts-data    #{{:cpu          {:capacity 10
                                                     :load     5.5}
                                      :metric       "cpu"
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:cpu          {:capacity 10
                                                     :load     5.5}
                                      :metric       "cpu"
                                      :nuvlaedge-id nuvlabox-id-2
                                      :timestamp    (time/to-str update-time-2)}}}]
                     (update-in (:cpu-stats raw-metric-data) [0 :ts-data] set)))
              (is (= [{:dimensions {:nuvlaedge-count 4}
                       :ts-data    #{{:metric       "ram"
                                      :nuvlaedge-id nuvlabox-id
                                      :ram          {:capacity 4096
                                                     :used     2000}
                                      :timestamp    (time/to-str update-time)}
                                     {:metric       "ram"
                                      :nuvlaedge-id nuvlabox-id-2
                                      :ram          {:capacity 4096
                                                     :used     2000}
                                      :timestamp    (time/to-str update-time-2)}}}]
                     (update-in (:ram-stats raw-metric-data) [0 :ts-data] set)))
              (is (= [{:dimensions {:nuvlaedge-count 4}
                       :ts-data    #{{:disk         {:capacity 20000
                                                     :device   "datastore"
                                                     :used     15000}
                                      :metric       "disk"
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:disk         {:capacity 20000
                                                     :device   "root"
                                                     :used     20000}
                                      :metric       "disk"
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:disk         {:capacity 20000
                                                     :device   "datastore"
                                                     :used     15000}
                                      :metric       "disk"
                                      :nuvlaedge-id nuvlabox-id-2
                                      :timestamp    (time/to-str update-time-2)}
                                     {:disk         {:capacity 20000
                                                     :device   "root"
                                                     :used     20000}
                                      :metric       "disk"
                                      :nuvlaedge-id nuvlabox-id-2
                                      :timestamp    (time/to-str update-time-2)}}}]
                     (update-in (:disk-stats raw-metric-data) [0 :ts-data] set)))
              (is (= [{:dimensions {:nuvlaedge-count 4}
                       :ts-data    #{{:metric       "network"
                                      :network      {:bytes-received    3019.0
                                                     :bytes-transmitted 78.0
                                                     :interface         "vpn"}
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:metric       "network"
                                      :network      {:bytes-received    5579821.0
                                                     :bytes-transmitted 44145.0
                                                     :interface         "eth0"}
                                      :nuvlaedge-id nuvlabox-id
                                      :timestamp    (time/to-str update-time)}
                                     {:metric       "network"
                                      :network      {:bytes-received    3019.0
                                                     :bytes-transmitted 78.0
                                                     :interface         "vpn"}
                                      :nuvlaedge-id nuvlabox-id-2
                                      :timestamp    (time/to-str update-time-2)}
                                     {:metric       "network"
                                      :network      {:bytes-received    5579821.0
                                                     :bytes-transmitted 44145.0
                                                     :interface         "eth0"}
                                      :nuvlaedge-id nuvlabox-id-2
                                      :timestamp    (time/to-str update-time-2)}}}]
                     (update-in (:network-stats raw-metric-data) [0 :ts-data] set)))
              (is (= [{:dimensions {:nuvlaedge-count 4}
                       :ts-data    #{{:metric            "power-consumption"
                                      :nuvlaedge-id      nuvlabox-id
                                      :power-consumption {:energy-consumption 2.4
                                                          :metric-name        "IN_current"
                                                          :unit               "A"}
                                      :timestamp         (time/to-str update-time)}
                                     {:metric            "power-consumption"
                                      :nuvlaedge-id      nuvlabox-id-2
                                      :power-consumption {:energy-consumption 2.4
                                                          :metric-name        "IN_current"
                                                          :unit               "A"}
                                      :timestamp         (time/to-str update-time-2)}}}]
                     (update-in (:power-consumption-stats raw-metric-data) [0 :ts-data] set)))))

          (testing "custom es aggregations metric data query"
            (let [from (time/minus (time/now) (time/duration-unit 1 :days))
                  to   now]
              (testing "custom aggregation on cpu-stats"
                (let [custom-cpu-agg (-> (metrics-request
                                           {:datasets               ["cpu-stats"]
                                            :from                   from
                                            :to                     to
                                            :custom-es-aggregations {:agg1 {:date_histogram
                                                                            {:field          "@timestamp"
                                                                             :fixed_interval "1d"
                                                                             :min_doc_count  0}
                                                                            :aggregations {:avg-cpu-load {:avg {:field :cpu.load}}
                                                                                           :min-cpu-load {:min {:field :cpu.load}}
                                                                                           :max-cpu-load {:max {:field :cpu.load}}}}}})
                                         (ltu/is-status 200)
                                         (ltu/body->edn)
                                         (ltu/body))]
                  (is (= [{:dimensions {:nuvlaedge-count 4}
                           :agg1       [{:aggregations {:avg-cpu-load {:value 5.5}
                                                        :max-cpu-load {:value 5.5}
                                                        :min-cpu-load {:value 5.5}}
                                         :doc-count    2
                                         :timestamp    (time/to-str midnight-today)}]}]
                         (:cpu-stats custom-cpu-agg)))))
              (testing "custom aggregation on disk-stats"
                (let [custom-disk-agg (-> (metrics-request
                                            {:datasets               ["disk-stats"]
                                             :from                   from
                                             :to                     to
                                             :custom-es-aggregations {:agg1 {:date_histogram
                                                                             {:field          "@timestamp"
                                                                              :fixed_interval "1d"
                                                                              :min_doc_count  0}
                                                                             :aggregations {:total-disk-capacity {:sum {:field :disk.capacity}}
                                                                                            :total-disk-used     {:sum {:field :disk.used}}}}}})
                                          (ltu/is-status 200)
                                          (ltu/body->edn)
                                          (ltu/body))]
                  (is (= [{:dimensions {:nuvlaedge-count 4}
                           :agg1       [{:aggregations {:total-disk-capacity {:value 80000.0}
                                                        :total-disk-used     {:value 70000.0}}
                                         :doc-count    4
                                         :timestamp    (time/to-str midnight-today)}]}]
                         (:disk-stats custom-disk-agg)))))))

          (check-accept-header metrics-request now)

          (testing "query request validation"
            (let [invalid-request (fn [options]
                                    (-> (metrics-request options)
                                        (ltu/is-status 400)
                                        (ltu/body->edn)
                                        (ltu/body)
                                        :message))]
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

          (testing "csv export of metrics data"
            (let [from        (time/minus now (time/duration-unit 1 :days))
                  to          now
                  csv-request (fn [dataset granularity]
                                (-> (metrics-request {:accept-header "text/csv"
                                                      :datasets      [dataset]
                                                      :from          from
                                                      :to            to
                                                      :granularity   granularity})
                                    (ltu/is-status 200)
                                    (ltu/is-header "Content-Type" "text/csv")
                                    (ltu/is-header "Content-disposition" "attachment;filename=export.csv")
                                    (ltu/body)))]
              (testing "Export with predefined aggregations"
                (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-avg-cpu-capacity,sum-avg-cpu-load,sum-avg-cpu-load-1,sum-avg-cpu-load-5,sum-context-switches,sum-interrupts,sum-software-interrupts,sum-system-calls\n"
                            (str/join "," [4
                                           (time/to-str midnight-yesterday)
                                           0, 0, 0, 0, 0, 0, 0, 0, 0]) "\n"
                            (str/join "," [4
                                           (time/to-str midnight-today)
                                           2, 20, 11, 0, 0, 0, 0, 0, 0]) "\n")
                       (csv-request "cpu-stats" "1-days")))
                (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-avg-ram-capacity,sum-avg-ram-used\n"
                            (str/join "," [4
                                           (time/to-str midnight-yesterday)
                                           0, 0, 0]) "\n"
                            (str/join "," [4
                                           (time/to-str midnight-today)
                                           2
                                           8192
                                           4000]) "\n")
                       (csv-request "ram-stats" "1-days")))
                (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-avg-disk-capacity,sum-avg-disk-used\n"
                            (str/join "," [4
                                           (time/to-str midnight-yesterday)
                                           0, 0, 0]) "\n"
                            (str/join "," [4
                                           (time/to-str midnight-today)
                                           4, 80000, 70000]) "\n")
                       (csv-request "disk-stats" "1-days")))
                (is (= (str "nuvlaedge-count,timestamp,doc-count,sum-bytes-received,sum-bytes-transmitted\n"
                            (str/join "," [4
                                           (time/to-str midnight-yesterday)
                                           0 0 0]) "\n"
                            (str/join "," [4
                                           (time/to-str midnight-today)
                                           4 11165680 88446]) "\n")
                       (csv-request "network-stats" "1-days")))
                (is (= (str "nuvlaedge-count,power-consumption.metric-name,timestamp,doc-count,sum-energy-consumption\n"
                            (str/join "," [4
                                           "IN_current"
                                           (time/to-str midnight-yesterday)
                                           0 0]) "\n"
                            (str/join "," [4
                                           "IN_current"
                                           (time/to-str midnight-today)
                                           2
                                           4.8]) "\n")
                       (csv-request "power-consumption-stats" "1-days"))))
              (testing "Export raw telemetry data"
                (is (= (str "timestamp,nuvlaedge-id,capacity,load\n"
                            (str/join "," [(time/to-str update-time)
                                           nuvlabox-id
                                           10 5.5]) "\n"
                            (str/join "," [(time/to-str update-time-2)
                                           nuvlabox-id-2
                                           10 5.5]) "\n")
                       (csv-request "cpu-stats" "raw")))
                (is (= (str "timestamp,nuvlaedge-id,capacity,used\n"
                            (str/join "," [(time/to-str update-time)
                                           nuvlabox-id
                                           4096 2000]) "\n"
                            (str/join "," [(time/to-str update-time-2)
                                           nuvlabox-id-2
                                           4096 2000]) "\n")
                       (csv-request "ram-stats" "raw")))
                (is (= #{"timestamp,nuvlaedge-id,capacity,device,used"
                         (str/join "," [(time/to-str update-time)
                                        nuvlabox-id
                                        20000 "root" 20000])
                         (str/join "," [(time/to-str update-time)
                                        nuvlabox-id
                                        20000 "datastore" 15000])
                         (str/join "," [(time/to-str update-time-2)
                                        nuvlabox-id-2
                                        20000 "root" 20000])
                         (str/join "," [(time/to-str update-time-2)
                                        nuvlabox-id-2
                                        20000 "datastore" 15000])}
                       (-> (csv-request "disk-stats" "raw")
                           str/split-lines
                           set)))
                (is (= #{"timestamp,nuvlaedge-id,bytes-received,bytes-transmitted,interface"
                         (str/join "," [(time/to-str update-time)
                                        nuvlabox-id
                                        3019 78 "vpn"])
                         (str/join "," [(time/to-str update-time)
                                        nuvlabox-id
                                        5579821 44145 "eth0"])
                         (str/join "," [(time/to-str update-time-2)
                                        nuvlabox-id-2
                                        3019 78 "vpn"])
                         (str/join "," [(time/to-str update-time-2)
                                        nuvlabox-id-2
                                        5579821 44145 "eth0"])}
                       (-> (csv-request "network-stats" "raw")
                           str/split-lines
                           set)))
                (is (= (str "timestamp,nuvlaedge-id,energy-consumption,metric-name,unit\n"
                            (str/join "," [(time/to-str update-time)
                                           nuvlabox-id
                                           2.4 "IN_current" "A"]) "\n"
                            (str/join "," [(time/to-str update-time-2)
                                           nuvlabox-id-2
                                           2.4 "IN_current" "A"]) "\n")
                       (csv-request "power-consumption-stats" "raw")))))))))))

(defn create-commissioned-nuvlabox
  [session body created]
  (with-redefs [time/now-str (constantly (time/to-str created))]
    (let [nuvlabox     (-> session
                           (request nuvlabox-base-uri
                                    :request-method :post
                                    :body (j/write-value-as-string body))
                           (ltu/body->edn)
                           (ltu/is-status 201))
          nuvlabox-id  (ltu/location nuvlabox)
          nuvlabox-url (str p/service-context nuvlabox-id)
          _            (-> session
                           (request (-> session
                                        (request nuvlabox-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/get-op-url :activate)))
                           (ltu/body->edn)
                           (ltu/is-status 200))
          _            (-> session
                           (request (-> session
                                        (request nuvlabox-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/get-op-url :commission)))
                           (ltu/body->edn)
                           (ltu/is-status 200))]
      nuvlabox-id)))

(defn create-availability-test-nuvlaboxes
  [session-user session-nb session-admin now]
  (let [now-5d             (time/minus now (time/duration-unit 5 :days))
        now-1d             (time/minus now (time/duration-unit 1 :days))
        midnight-today     (time/truncated-to-days now)
        midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
        yesterday-2am      (time/plus midnight-yesterday (time/duration-unit 2 :hours))
        yesterday-10am     (time/plus midnight-yesterday (time/duration-unit 10 :hours))
        ;; add a nuvlabox in state COMMISSIONED, first online 5 days ago, and down for 8 hours yesterday from 2am until 10am
        nuvlabox-id-2      (create-commissioned-nuvlabox session-user valid-nuvlabox2 now-5d)
        nuvlabox-url-2     (str p/service-context nuvlabox-id-2)
        heartbeat-op-2     (-> session-user
                               (request nuvlabox-url-2)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url nb-utils/action-heartbeat))
        set-offline-op-2   (str nuvlabox-url-2 "/" nb-utils/action-set-offline)
        _                  (with-redefs [time/now (constantly now-5d)]
                             (-> session-user
                                 (request heartbeat-op-2)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)))
        _                  (ltu/refresh-es-indices)
        _                  (with-redefs [time/now (constantly yesterday-2am)]
                             (-> session-admin
                                 (request set-offline-op-2)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)))
        _                  (ltu/refresh-es-indices)
        _                  (with-redefs [time/now (constantly yesterday-10am)]
                             (-> session-nb
                                 (request heartbeat-op-2)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)))
        _                  (ltu/refresh-es-indices)
        ;; and another nuvlabox in state COMMISSIONED, first online 1 day ago
        nuvlabox-id-3      (create-commissioned-nuvlabox session-user valid-nuvlabox3 now-5d)
        nuvlabox-url-3     (str p/service-context nuvlabox-id-3)
        heartbeat-op-3     (-> session-nb
                               (request nuvlabox-url-3)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url nb-utils/action-heartbeat))
        _                  (with-redefs [time/now (constantly now-1d)]
                             (-> session-nb
                                 (request heartbeat-op-3)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)))
        _                  (ltu/refresh-es-indices)
        ;; add yet another nuvlabox with created date 1 day ago, in state COMMISSIONED, but for which we send no metrics
        ;; as such it should be considered as always COMMISSIONED and always offline since creation
        nuvlabox-id-4      (create-commissioned-nuvlabox session-user valid-nuvlabox4 midnight-today)
        ;; and another one which we leave in state NEW, so it should not be considered as offline
        nuvlabox-id-5      (-> session-user
                               (request nuvlabox-base-uri
                                        :request-method :post
                                        :body (j/write-value-as-string (assoc valid-nuvlabox :created (time/to-str now-5d))))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))]
    [nuvlabox-id-2 nuvlabox-id-3 nuvlabox-id-4 nuvlabox-id-5]))

(deftest availability-data
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [now                (time/now)
          now-5d             (time/minus now (time/duration-unit 5 :days))
          now-2d             (time/minus now (time/duration-unit 2 :days))
          now-1d             (time/minus now (time/duration-unit 1 :days))
          now-12h            (time/minus now (time/duration-unit 12 :hours))
          now-1s             (time/minus now (time/duration-unit 1 :seconds))
          midnight-today     (time/truncated-to-days now)
          midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
          yesterday-2am      (time/plus midnight-yesterday (time/duration-unit 2 :hours))
          yesterday-10am     (time/plus midnight-yesterday (time/duration-unit 10 :hours))
          session            (-> (ltu/ring-app)
                                 session
                                 (content-type "application/json"))
          session-admin      (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user       (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
          create-nuvlabox    (partial create-commissioned-nuvlabox session-user)
          nuvlabox-id        (create-nuvlabox valid-nuvlabox now-5d)
          nuvlabox-url       (str p/service-context nuvlabox-id)
          session-nb         (header session authn-info-header (str "user/jane user/jane group/nuvla-user group/nuvla-anon"))
          ;; session-nb      (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))
          heartbeat-op       (-> session-nb
                                 (request nuvlabox-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/get-op-url nb-utils/action-heartbeat))
          set-offline-op     (-> session-admin
                                 (request nuvlabox-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/get-op-url nb-utils/action-set-offline))]

      ;; first time online 2 days ago
      (with-redefs [time/now (constantly now-2d)]
        (-> session-nb
            (request heartbeat-op)
            (ltu/body->edn)
            (ltu/is-status 200)))
      (ltu/refresh-es-indices)

      ;; went offline 1 day ago
      (with-redefs [time/now (constantly now-1d)]
        (-> session-admin
            (request set-offline-op)
            (ltu/body->edn)
            (ltu/is-status 200)))
      (ltu/refresh-es-indices)

      ;; back online 12 hours ago
      (with-redefs [time/now (constantly now-12h)]
        (-> session-nb
            (request heartbeat-op)
            (ltu/body->edn)
            (ltu/is-status 200)))
      (ltu/refresh-es-indices)

      ;; offline again 1 second ago
      (with-redefs [time/now (constantly now-1s)]
        (-> session-admin
            (request set-offline-op)
            (ltu/body->edn)
            (ltu/is-status 200)))
      (ltu/refresh-es-indices)

      (same.core/with-comparator
        (compare-ulp 100.0 1e12)

        (testing "availability data on a single nuvlabox"
          (let [nuvlabox-data-url (str nuvlabox-url "/data")
                metrics-request   (fn [{:keys [datasets from from-str to to-str granularity accept-header] #_:or #_{accept-header "application/json"}}]
                                    (ltu/refresh-es-indices)
                                    (-> session-nb
                                        (content-type "application/x-www-form-urlencoded")
                                        (cond-> accept-header (header "accept" accept-header))
                                        (request nuvlabox-data-url
                                                 :body (rc/form-encode
                                                         {:dataset     datasets
                                                          :from        (if from (time/to-str from) from-str)
                                                          :to          (if to (time/to-str to) to-str)
                                                          :granularity granularity}))))]
            (testing "from midnight yesterday until now"
              (let [from        midnight-yesterday
                    to          now
                    metric-data (-> (metrics-request {:datasets    ["availability-stats"]
                                                      :from        from
                                                      :to          to
                                                      :granularity "1-days"})
                                    (ltu/is-status 200)
                                    (ltu/body->edn)
                                    (ltu/body))]
                (is (ish? [{:dimensions {:nuvlaedge-id nuvlabox-id}
                            :ts-data    (if (time/after? now-12h midnight-today)
                                          [{:timestamp    (time/to-str midnight-yesterday)
                                            :doc-count    1
                                            :aggregations {:avg-online {:value (double (/ (time/time-between midnight-yesterday now-1d :seconds)
                                                                                          (time/time-between midnight-yesterday midnight-today :seconds)))}}}
                                           {:timestamp    (time/to-str midnight-today)
                                            :doc-count    2
                                            :aggregations {:avg-online {:value (double (/ (* 3600 12)
                                                                                          (time/time-between midnight-today to :seconds)))}}}]
                                          [{:timestamp    (time/to-str midnight-yesterday)
                                            :doc-count    2
                                            :aggregations {:avg-online {:value (double (/ (+ (time/time-between midnight-yesterday now-1d :seconds)
                                                                                             (time/time-between now-12h midnight-today :seconds))
                                                                                          (time/time-between midnight-yesterday midnight-today :seconds)))}}}
                                           {:timestamp    (time/to-str midnight-today)
                                            :doc-count    1
                                            :aggregations {:avg-online {:value (double (/ (if (time/after? now-12h midnight-today)
                                                                                            (* 3600 12)
                                                                                            (time/time-between midnight-today now-1s :seconds))
                                                                                          (time/time-between midnight-today to :seconds)))}}}])}]
                          (:availability-stats metric-data)))))

            (testing "raw availability data query"
              (let [from                  (time/minus now (time/duration-unit 1 :days))
                    to                    now
                    raw-availability-data (-> (metrics-request {:datasets    ["availability-stats"]
                                                                :from        from
                                                                :to          to
                                                                :granularity "raw"})
                                              (ltu/is-status 200)
                                              (ltu/body->edn)
                                              (ltu/body))]
                (is (= [{:dimensions {:nuvlaedge-id nuvlabox-id}
                         :ts-data    [{:nuvlaedge-id nuvlabox-id
                                       :timestamp    (time/to-str now-12h)
                                       :online       1}
                                      {:nuvlaedge-id nuvlabox-id
                                       :timestamp    (time/to-str now-1s)
                                       :online       0}]}]
                       (:availability-stats raw-availability-data)))))

            (testing "csv export of availability data"
              (let [from        midnight-yesterday
                    to          now
                    csv-request (fn [dataset granularity]
                                  (-> (metrics-request {:accept-header "text/csv"
                                                        :datasets      [dataset]
                                                        :from          from
                                                        :to            to
                                                        :granularity   granularity})
                                      (ltu/is-status 200)
                                      (ltu/is-header "Content-Type" "text/csv")
                                      (ltu/is-header "Content-disposition" "attachment;filename=export.csv")
                                      (ltu/body)))]
                (testing "export with predefined aggregations"
                  (is (= (str "timestamp,doc-count,avg-online")
                         (-> (csv-request "availability-stats" "1-days")
                             str/split-lines
                             first)))
                  (is (ish? (if (time/after? now-12h midnight-today)
                              [[(time/to-str midnight-yesterday)
                                1 (double (/ (time/time-between midnight-yesterday now-1d :seconds)
                                             (time/time-between midnight-yesterday midnight-today :seconds)))]
                               [(time/to-str midnight-today)
                                2 (double (/ (* 3600 12)
                                             (time/time-between midnight-today to :seconds)))]]
                              [[(time/to-str midnight-yesterday)
                                2 (double (/ (+ (time/time-between midnight-yesterday now-1d :seconds)
                                                (time/time-between now-12h midnight-today :seconds))
                                             (time/time-between midnight-yesterday midnight-today :seconds)))]
                               [(time/to-str midnight-today)
                                1 (double (/ (if (time/after? now-12h midnight-today)
                                               (* 3600 12)
                                               (time/time-between midnight-today now-1s :seconds))
                                             (time/time-between midnight-today to :seconds)))]])
                            (->> (csv-request "availability-stats" "1-days")
                                 str/split-lines
                                 rest
                                 (map #(str/split % #","))
                                 (map (fn [v] (-> v
                                                  (update 1 #(some-> % Integer/parseInt))
                                                  (update 2 #(some-> % Double/parseDouble)))))))))
                (testing "export raw availability data"
                  (is (= (str "timestamp,online\n"
                              (str/join "," [(time/to-str now-1d)
                                             0]) "\n"
                              (str/join "," [(time/to-str now-12h)
                                             1]) "\n"
                              (str/join "," [(time/to-str now-1s)
                                             0]) "\n")
                         (csv-request "availability-stats" "raw"))))))))

        (testing "availability data across multiple nuvlaboxes"
          (let [[nuvlabox-id-2 nuvlabox-id-3 nuvlabox-id-4 nuvlabox-id-5] (create-availability-test-nuvlaboxes
                                                                            session-user session-nb session-admin now)
                nuvlabox-data-url  (str p/service-context nb/resource-type "/data")
                midnight-today     (time/truncated-to-days now)
                midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
                metrics-request    (fn [{:keys [datasets from from-str to to-str granularity accept-header]}]
                                     (ltu/refresh-es-indices)
                                     (-> session-nb
                                         (cond-> accept-header (header "accept" accept-header))
                                         (request nuvlabox-data-url
                                                  :request-method :patch
                                                  :headers {:bulk true}
                                                  :body (j/write-value-as-string
                                                          {:filter      (str "(id='" nuvlabox-id-2 "'"
                                                                             " or id='" nuvlabox-id-3 "'"
                                                                             " or id='" nuvlabox-id-4 "'"
                                                                             " or id='" nuvlabox-id-5 "')")
                                                           :dataset     datasets
                                                           :from        (if from (time/to-str from) from-str)
                                                           :to          (if to (time/to-str to) to-str)
                                                           :granularity granularity}))))]
            (testing "new metrics data is added to ts-nuvlaedge time-serie"
              (let [from        midnight-yesterday
                    to          now
                    metric-data (-> (metrics-request {:datasets    ["availability-stats"
                                                                    "availability-by-edge"]
                                                      :from        from
                                                      :to          to
                                                      :granularity "1-days"})
                                    (ltu/is-status 200)
                                    (ltu/body->edn)
                                    (ltu/body))]
                (is (ish? [{:dimensions {:nuvlaedge-count 3}
                            :ts-data    [;; yesterday:
                                         ;; edge2 was down 8 hours => 2/3 available
                                         ;; edge3 came up the first time in the middle of the day, but still it should be counted as 100% available
                                         ;; edge4 (not sending data) should be counted as commissioned and offline since creation on the 2nd day
                                         ;; edge5 should not be counted (not commissioned)
                                         (let [seconds-in-day          (* 3600 24)
                                               seconds-edge3-yesterday (time/time-between now-1d midnight-today :seconds)
                                               global-avg-online       (double (/ (+ (* 2/3 seconds-in-day) seconds-edge3-yesterday)
                                                                                  (+ seconds-in-day seconds-edge3-yesterday)))
                                               online-edges            (* 2 global-avg-online)]
                                           {:timestamp    (time/to-str midnight-yesterday)
                                            :doc-count    3
                                            :aggregations {:edges-count           {:value 2}
                                                           :virtual-edges-offline {:value (- 2 online-edges)}
                                                           :virtual-edges-online  {:value online-edges}}})
                                         {:timestamp    (time/to-str midnight-today)
                                          :doc-count    0
                                          :aggregations {:edges-count           {:value 3}
                                                         :virtual-edges-offline {:value 1}
                                                         :virtual-edges-online  {:value 2}}}]}]
                          (:availability-stats metric-data)))
                (is (ish? [{:dimensions {:nuvlaedge-count 3}
                            :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                          :doc-count    3
                                          :aggregations {:global-avg-online {:value (double (/ (+ 1 2/3) 2))}
                                                         :by-edge
                                                         {:buckets
                                                          #{{:edge-avg-online {:value (double 2/3)}
                                                             :key             nuvlabox-id-2
                                                             :name            nb-name2}
                                                            {:edge-avg-online {:value 1.0}
                                                             :key             nuvlabox-id-3
                                                             :name            nb-name3}}}
                                                         :edges-count       {:value 2}}}
                                         {:timestamp    (time/to-str midnight-today)
                                          :doc-count    0
                                          :aggregations {:global-avg-online {:value (double (/ 2 3))}
                                                         :by-edge
                                                         {:buckets
                                                          #{{:edge-avg-online {:value 1.0}
                                                             :key             nuvlabox-id-2
                                                             :name            nb-name2}
                                                            {:edge-avg-online {:value 1.0}
                                                             :key             nuvlabox-id-3
                                                             :name            nb-name3}
                                                            {:edge-avg-online {:value 0.0}
                                                             :key             nuvlabox-id-4
                                                             :name            nb-name4}}}
                                                         :edges-count       {:value 3}}}]}]
                          (-> (:availability-by-edge metric-data)
                              (update-in [0 :ts-data 0 :aggregations :by-edge :buckets] set)
                              (update-in [0 :ts-data 1 :aggregations :by-edge :buckets] set))))))

            (testing "raw availability data query"
              (let [from                  midnight-yesterday
                    to                    now
                    raw-availability-data (-> (metrics-request {:datasets    ["availability-stats"]
                                                                :from        from
                                                                :to          to
                                                                :granularity "raw"})
                                              (ltu/is-status 200)
                                              (ltu/body->edn)
                                              (ltu/body))]
                (is (= [{:dimensions {:nuvlaedge-count 3}
                         :ts-data    (->> [{:nuvlaedge-id nuvlabox-id-2
                                            :online       0
                                            :timestamp    (time/to-str yesterday-2am)}
                                           {:nuvlaedge-id nuvlabox-id-2
                                            :online       1
                                            :timestamp    (time/to-str yesterday-10am)}
                                           {:nuvlaedge-id nuvlabox-id-3
                                            :online       1
                                            :timestamp    (time/to-str now-1d)}]
                                          (sort-by :timestamp)
                                          vec)}]
                       (:availability-stats raw-availability-data)))))

            (testing "csv export of availability data"
              (let [csv-request (fn [dataset granularity]
                                  (-> (metrics-request {:accept-header "text/csv"
                                                        :datasets      [dataset]
                                                        :from          midnight-yesterday
                                                        :to            now
                                                        :granularity   granularity})
                                      (ltu/is-status 200)
                                      (ltu/is-header "Content-Type" "text/csv")
                                      (ltu/is-header "Content-disposition" "attachment;filename=export.csv")
                                      (ltu/body)))
                    fmt         #(.format (DecimalFormat. "0.####" (DecimalFormatSymbols. Locale/US)) %)]
                (testing "export with predefined aggregations"
                  (is (= (str "nuvlaedge-count,timestamp,doc-count,edges-count,virtual-edges-online,virtual-edges-offline\n"
                              (let [seconds-in-day          (* 3600 24)
                                    seconds-edge3-yesterday (time/time-between now-1d midnight-today :seconds)
                                    global-avg-online       (double (/ (+ (* 2/3 seconds-in-day) seconds-edge3-yesterday)
                                                                       (+ seconds-in-day seconds-edge3-yesterday)))
                                    online-edges            (* 2 global-avg-online)]
                                (str/join "," [3
                                               (time/to-str midnight-yesterday)
                                               3, 2, (fmt online-edges), (fmt (- 2 online-edges))])) "\n"
                              (str/join "," [3
                                             (time/to-str midnight-today)
                                             0, 3, 2, 1]) "\n")
                         (csv-request "availability-stats" "1-days"))))
                (testing "export raw availability data"
                  (is (= (str "timestamp,nuvlaedge-id,online\n"
                              (str/join "\n" (sort [(str/join "," [(time/to-str yesterday-2am)
                                                                   nuvlabox-id-2, 0])
                                                    (str/join "," [(time/to-str yesterday-10am)
                                                                   nuvlabox-id-2, 1])
                                                    (str/join "," [(time/to-str now-1d)
                                                                   nuvlabox-id-3, 1])]))
                              "\n")
                         (csv-request "availability-stats" "raw"))))))))))))

(comment
  ;; stress test availability-data test to find corner cases
  (let [random-offset (rand-int (* 3600 24)) #_65328 #_535 #_10430 #_3815 #_[8967 3815]]
    (with-redefs [time/now (constantly (time/plus (time/parse-date "2024-07-18T00:00:00.000Z")
                                                  (time/duration-unit random-offset :seconds)))]
      #_(availability-data)
      (let [failure? (atom false)]
        (defmethod clojure.test/report :fail [m]
          (reset! failure? true))
        (loop [n 100]
          (when (pos? n)
            (availability-data)
            (if @failure?
              :failure-detected
              (recur (dec n))))))))
  )

(deftest issue-tasklist-3132-non-regression-test
  (let [nuvlaboxes [{:id      "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296"
                     :created "2024-05-18T16:37:02.291Z"}
                    {:id      "nuvlabox/30393fec-ac24-47a6-bd6b-594009915653"
                     :created "2024-05-21T16:50:10.759Z"}]
        raw-data   [{:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-18T16:44:57.467Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-19T00:52:30.555Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-19T00:52:58.052Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-19T16:42:27.469Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-19T16:43:38.391Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-20T05:15:59.058Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-20T05:16:05.215Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-20T05:16:05.504Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-20T06:26:14.066Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-20T06:26:36.013Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-20T15:42:01.895Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-20T15:42:10.314Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-21T07:59:26.990Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T07:59:29.936Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-21T09:15:43.551Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T09:17:06.363Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-21T10:16:24.442Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T10:16:27.650Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T10:16:27.857Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-21T10:46:00.914Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T10:46:32.874Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-21T11:54:14.836Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T11:55:10.153Z"}
                    {:nuvlaedge-id "nuvlabox/30393fec-ac24-47a6-bd6b-594009915653",
                     :online       1,
                     :timestamp    "2024-05-21T16:51:25.987Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-21T17:46:22.554Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-21T17:46:54.814Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       0,
                     :timestamp    "2024-05-22T05:14:51.920Z"}
                    {:nuvlaedge-id "nuvlabox/3e93cdc3-341e-46d4-a811-f4db791d7296",
                     :online       1,
                     :timestamp    "2024-05-22T05:15:07.649Z"}
                    {:nuvlaedge-id "nuvlabox/30393fec-ac24-47a6-bd6b-594009915653",
                     :online       0,
                     :timestamp    "2024-05-22T05:30:26.498Z"}]
        body       {:filter      "(id!=null)",
                    :dataset     ["availability-stats"],
                    :from        "2024-05-15T07:03:24Z",
                    :to          "2024-05-22T07:03:24Z",
                    :granularity "1-hours"}
        request    {:headers {"content-type" "application/json"}
                    :body    body}
        start      (time/parse-date "2024-05-15T07:00:00.000Z")
        end        (time/parse-date "2024-05-22T07:00:00.000Z")]
    (with-redefs [data-utils/fetch-nuvlaboxes       (constantly nuvlaboxes)
                  data-utils/query-availability-raw (fn [_ & args]
                                                      (if (some? (first args))
                                                        [0 [] []]
                                                        [(count raw-data)
                                                         (mapv (fn [{:keys [timestamp nuvlaedge-id online]}]
                                                                 {:_source {(keyword "@timestamp") timestamp
                                                                            :nuvlaedge-id          nuvlaedge-id
                                                                            :online                online}
                                                                  :sort    [(time/unix-timestamp-from-date (time/parse-date timestamp))
                                                                            nuvlaedge-id]})
                                                               raw-data)
                                                         (loop [date  start
                                                                dates [start]]
                                                           (if (time/before? date end)
                                                             (recur (time/plus date (time/duration-unit 1 :hours))
                                                                    (conj dates date))
                                                             (conj dates end)))]))]
      (let [data (-> (data-utils/wrapped-query-data
                       (assoc body :mode :multi-edge-query)
                       request)
                     :body
                     (get "availability-stats")
                     first
                     :ts-data)]
        (is (every? #(not (neg? %)) (map (comp :value :virtual-edges-online :aggregations) data)))))))

(deftest availability-perf-test
  ;; Perf tests are commented out because it takes long time to insert 10k nuvlaedges.
  ;; Uncomment locally and run them as needed.
  #_(binding [config-nuvla/*stripe-api-key* nil]
      (let [now           (time/now)
            now-1d        (time/minus now (time/duration-unit 1 :days))
            session       (-> (ltu/ring-app)
                              session
                              (content-type "application/json"))
            session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
            session-user  (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
            session-nb    (header session authn-info-header (str "user/jane user/jane group/nuvla-user group/nuvla-anon"))]
        (testing "performance test querying multiple muvlaboxes"
          (let [n 2500]                                     ; n 2500 => 10k nuvlaboxes
            (dotimes [_i n]
              (create-availability-test-nuvlaboxes
                session-user session-nb session-admin now))
            (ltu/refresh-es-indices)

            (let [nuvlabox-data-url  (str p/service-context nb/resource-type "/data")
                  midnight-today     (time/truncated-to-days now)
                  midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
                  metrics-request    (fn [{:keys [datasets from from-str to to-str granularity accept-header]}]
                                       (-> session-nb
                                           (cond-> accept-header (header "accept" accept-header))
                                           (request nuvlabox-data-url
                                                    :request-method :patch
                                                    :headers {:bulk true}
                                                    :body (j/write-value-as-string
                                                            {:dataset     datasets
                                                             :from        (if from (time/to-str from) from-str)
                                                             :to          (if to (time/to-str to) to-str)
                                                             :granularity granularity}))))
                  from               midnight-yesterday
                  to                 now]

              (testing "make sure long running availability computations are interrupted after timeout"
                (let [from now-1d
                      to   now]
                  (with-redefs [data-utils/query-data-max-time 100]
                    (-> (metrics-request {:datasets    ["availability-stats"]
                                          :from        from
                                          :to          to
                                          :granularity "1-days"})
                        (ltu/is-status 504)
                        (ltu/body->edn)
                        (ltu/body)))))

              (testing "availability query performance"
                (let [[elapsed-time metric-data]
                      (logt/logtime1
                        (-> (metrics-request {:datasets    ["availability-stats"]
                                              :from        from
                                              :to          to
                                              :granularity "1-days"})
                            (ltu/is-status 200)
                            (ltu/body->edn)
                            (ltu/body)))
                      n-av-edges (* 2 n)]
                  (is (< elapsed-time 25000))
                  (is (ish? [{:dimensions {:nuvlaedge-count n-av-edges}
                              :ts-data    [;; yesterday:
                                           ;; edge2 was down 8 hours => 2/3 available
                                           ;; edge3 came up the first time in the middle of the day, but still it should be counted as 100% available
                                           ;; edge4 and edge5 should not be counted (not sending data and not commissioned)
                                           (let [seconds-in-day          (* 3600 24)
                                                 seconds-edge3-yesterday (time/time-between now-1d midnight-today :seconds)
                                                 global-avg-online       (double (/ (+ (* 2/3 seconds-in-day) seconds-edge3-yesterday)
                                                                                    (+ seconds-in-day seconds-edge3-yesterday)))
                                                 online-edges            (* n-av-edges global-avg-online)]
                                             {:timestamp    (time/to-str midnight-yesterday)
                                              :doc-count    (* 3 n)
                                              :aggregations {:edges-count           {:value n-av-edges}
                                                             :virtual-edges-offline {:value (- n-av-edges online-edges)}
                                                             :virtual-edges-online  {:value online-edges}}})
                                           {:timestamp    (time/to-str midnight-today)
                                            :doc-count    0
                                            :aggregations {:edges-count           {:value n-av-edges}
                                                           :virtual-edges-offline {:value 0}
                                                           :virtual-edges-online  {:value n-av-edges}}}]}]
                            (:availability-stats metric-data)))))))))))

(deftest lifecycle-online-next-heartbeat
  (test-online-next-heartbeat))

(deftest lifecycle-swarm-enabled
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-user  (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

          nuvlabox-id   (-> session-user
                            (request nuvlabox-base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string valid-nuvlabox))
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
                   :body (j/write-value-as-string {:swarm-endpoint "https://swarm.example.com"}))
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
                     :body (j/write-value-as-string {:orchestrator "swarm"}))
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
                     :body (j/write-value-as-string {}))
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
                     :body (j/write-value-as-string {:cluster-node-role "manager"}))
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


