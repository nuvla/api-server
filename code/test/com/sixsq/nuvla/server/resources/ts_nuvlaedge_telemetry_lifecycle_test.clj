(ns com.sixsq.nuvla.server.resources.ts-nuvlaedge-telemetry-lifecycle-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.ts-nuvlaedge-telemetry :as t]
    [com.sixsq.nuvla.server.util.time :as time]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest insert
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        timestamp     (time/now-str)
        entries       [{:nuvlaedge-id "nuvlabox/1"
                        :metric       "cpu"
                        :cpu          {:capacity            8
                                       :load                4.5
                                       :load-1              4.3
                                       :load-5              5.5
                                       :system-calls        0
                                       :interrupts          13621648
                                       :software-interrupts 37244
                                       :context-switches    382731}
                        :timestamp    timestamp}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "ram"
                        :ram          {:capacity 4096
                                       :used     1000}
                        :timestamp    timestamp}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "disk"
                        :disk         {:device   "root"
                                       :capacity 20000
                                       :used     10000}
                        :timestamp    timestamp}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    5247943
                                       :bytes-transmitted 41213}
                        :timestamp    timestamp}
                       {:nuvlaedge-id      "nuvlabox/1"
                        :metric            "power-consumption"
                        :power-consumption {:metric-name        "IN_current"
                                            :energy-consumption 2.4
                                            :unit               "A"}
                        :timestamp         timestamp}]]

    ;; bulk insert a few entries

    (-> session-admin
        (request (str base-uri "/bulk-insert")
                 :headers {:bulk true}
                 :request-method :patch
                 :body (j/write-value-as-string entries))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :errors false)
        (ltu/is-key-value count :items (count entries)))

    (testing "Wrong format data should throw invalid spec"
      (let [invalid-entry {:cpu       {:capacity            8
                                       :load                4.5
                                       :load-1              4.3
                                       :load-5              5.5
                                       :system-calls        0
                                       :interrupts          13621648
                                       :software-interrupts 37244
                                       :context-switches    382731}
                           :timestamp (time/to-str (time/from-now 1 :seconds))}]
        (-> session-admin
            (request (str base-uri "/bulk-insert")
                     :headers {:bulk true}
                     :request-method :patch
                     :body (j/write-value-as-string [invalid-entry]))
            (ltu/body->edn)
            (ltu/is-status 400))))

    (testing "Conflicting timestamps with same dimension values should throw an error"
      (let [timestamp           (time/now-str)
            conflicting-entries [{:nuvlaedge-id "nuvlabox/1"
                                  :metric       "ram"
                                  :ram          {:capacity 4096
                                                 :used     1000}
                                  :timestamp    timestamp}
                                 {:nuvlaedge-id "nuvlabox/1"
                                  :metric       "ram"
                                  :ram          {:capacity 4096
                                                 :used     1001}
                                  :timestamp    timestamp}]]
        (-> session-admin
            (request (str base-uri "/bulk-insert")
                     :headers {:bulk true}
                     :request-method :patch
                     :body (j/write-value-as-string conflicting-entries))
            (ltu/body->edn)
            (ltu/is-status 400))))))

(deftest query-ram
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        now           (time/truncated-to-minutes (time/now))
        entries       [{:nuvlaedge-id "nuvlabox/1"
                        :metric       "ram"
                        :ram          {:capacity 4096
                                       :used     1000}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 30 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "ram"
                        :ram          {:capacity 4096
                                       :used     1500}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 60 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "ram"
                        :ram          {:capacity 4096
                                       :used     2000}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 90 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "ram"
                        :ram          {:capacity 4096
                                       :used     1020}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 120 :seconds)))}]]

    (-> session-admin
        (request (str base-uri "/bulk-insert")
                 :headers {:bulk true}
                 :request-method :patch
                 :body (j/write-value-as-string entries))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :errors false)
        (ltu/is-key-value count :items (count entries)))

    (ltu/refresh-es-indices)

    (testing "Query ram metrics"
      (-> session-admin
          (content-type "application/x-www-form-urlencoded")
          (request base-uri
                   :request-method :put
                   :body (rc/form-encode
                           {:last 0
                            :tsds-aggregation
                            (j/write-value-as-string
                              {:aggregations
                               {:tsds-stats
                                {:date_histogram
                                 {:field          "@timestamp"
                                  :fixed_interval "1d"}
                                 :aggregations
                                 {:avg-ram {:avg {:field :ram.used}}}}}})}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value (comp :value :avg-ram first :buckets :tsds-stats)
                            :aggregations
                            1380.0)
          (ltu/is-count 4))

      (let [body (-> session-admin
                     (content-type "application/x-www-form-urlencoded")
                     (request base-uri
                              :request-method :put
                              :body (rc/form-encode
                                      {:last 0
                                       :tsds-aggregation
                                       (j/write-value-as-string
                                         {:aggregations
                                          {:tsds-stats
                                           {:date_histogram
                                            {:field          "@timestamp"
                                             :fixed_interval "30s"}
                                            :aggregations
                                            {:avg-ram {:avg {:field :ram.used}}}}}})}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/is-count 4)
                     (ltu/body))]
        (is (= (map (fn [{:keys [timestamp ram]}]
                      {:key_as_string timestamp
                       :doc_count     1
                       :avg-ram       {:value (double (:used ram))}})
                    entries)
               (->> body :aggregations :tsds-stats :buckets
                    (map #(dissoc % :key)))))))))

(deftest query-network
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        now           (time/truncated-to-minutes (time/now))
        entries       [{:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    10
                                       :bytes-transmitted 10}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 30 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    20
                                       :bytes-transmitted 20}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 60 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    25
                                       :bytes-transmitted 25}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 90 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    40
                                       :bytes-transmitted 40}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 120 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    5
                                       :bytes-transmitted 5}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 150 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    10
                                       :bytes-transmitted 10}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 180 :seconds)))}
                       {:nuvlaedge-id "nuvlabox/1"
                        :metric       "network"
                        :network      {:interface         "eth0"
                                       :bytes-received    20
                                       :bytes-transmitted 20}
                        :timestamp    (time/to-str (time/plus now (time/duration-unit 210 :seconds)))}]]

    (-> session-admin
        (request (str base-uri "/bulk-insert")
                 :headers {:bulk true}
                 :request-method :patch
                 :body (j/write-value-as-string entries))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :errors false)
        (ltu/is-key-value count :items (count entries)))

    (ltu/refresh-es-indices)

    (testing "Query network metrics"
      (-> session-admin
          (content-type "application/x-www-form-urlencoded")
          (request base-uri
                   :request-method :put
                   :body (rc/form-encode
                           {:last 0
                            :tsds-aggregation
                            (j/write-value-as-string
                              {:aggregations
                               {:tsds-stats
                                {:date_histogram
                                 {:field          "@timestamp"
                                  :fixed_interval "1d"}
                                 :aggregations
                                 {:max-bytes-tx {:max {:field :network.bytes-transmitted}}
                                  :max-bytes-rx {:max {:field :network.bytes-received}}}}}})}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value (comp :value :max-bytes-tx first :buckets :tsds-stats)
                            :aggregations
                            40.0)
          (ltu/is-key-value (comp :value :max-bytes-rx first :buckets :tsds-stats)
                            :aggregations
                            40.0)
          (ltu/is-count 7))

      (let [body (-> session-admin
                     (content-type "application/x-www-form-urlencoded")
                     (request base-uri
                              :request-method :put
                              :body (rc/form-encode
                                      {:last 0
                                       :tsds-aggregation
                                       (j/write-value-as-string
                                         {:aggregations
                                          {:tsds-stats
                                           {:date_histogram
                                            {:field          "@timestamp"
                                             :fixed_interval "30s"}
                                            :aggregations
                                            {:max-bytes-tx {:max {:field :network.bytes-transmitted}}
                                             :max-bytes-rx {:max {:field :network.bytes-received}}}}}})}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/is-count 7)
                     (ltu/body))]
        (is (= (map (fn [{:keys [timestamp network]}]
                      {:key_as_string timestamp
                       :doc_count     1
                       :max-bytes-tx  {:value (double (:bytes-transmitted network))}
                       :max-bytes-rx  {:value (double (:bytes-received network))}})
                    entries)
               (->> body :aggregations :tsds-stats :buckets
                    (map #(dissoc % :key))))))

      (-> session-admin
          (content-type "application/x-www-form-urlencoded")
          (request base-uri
                   :request-method :put
                   :body (rc/form-encode
                           {:last 0
                            :tsds-aggregation
                            (j/write-value-as-string
                              {:aggregations
                               {:tsds-stats
                                {:date_histogram
                                 {:field          "@timestamp"
                                  :fixed_interval "1d"}
                                 :aggregations
                                 {:stats-bytes-tx {:extended_stats {:field :network.bytes-transmitted}}
                                  :stats-bytes-rx {:extended_stats {:field :network.bytes-received}}}}}})}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 7)
          (ltu/is-key-value (comp #(format "%.2f" %) :std_deviation :stats-bytes-tx first :buckets :tsds-stats)
                            :aggregations
                            (format "%.2f" 10.93))
          (ltu/is-key-value (comp #(format "%.2f" %) :std_deviation :stats-bytes-rx first :buckets :tsds-stats)
                            :aggregations
                            (format "%.2f" 10.93))
          (ltu/body))

      (let [body             (-> session-admin
                                 (content-type "application/x-www-form-urlencoded")
                                 (request base-uri
                                          :request-method :put
                                          :body (rc/form-encode
                                                  {:last 0
                                                   :tsds-aggregation
                                                   (j/write-value-as-string
                                                     {:aggregations
                                                      {:tsds-stats
                                                       {:date_histogram
                                                        {:field          "@timestamp"
                                                         :fixed_interval "30s"}
                                                        :aggregations
                                                        {:max-tx           {:max {:field :network.bytes-transmitted}}
                                                         :rate-tx          {:derivative {:buckets_path "max-tx"}}
                                                         :only-pos-rate-tx {:bucket_script
                                                                            {:buckets_path {:rateTx "rate-tx"}
                                                                             :script       "if (params.rateTx > 0) { return params.rateTx } else { return null }"}}}}}})}))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/is-count 7)
                                 (ltu/body))
            only-pos-rate-tx (->> body :aggregations :tsds-stats :buckets
                                  (keep :only-pos-rate-tx)
                                  (map :value))]
        (is (= '(10.0 5.0 15.0 5.0 10.0) only-pos-rate-tx))))))

(deftest acl
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        entries      [{:nuvlaedge-id "nuvlabox/1"
                       :metric       "ram"
                       :ram          {:capacity 4096
                                      :used     1000}
                       :timestamp    (time/now-str)}]]
    (testing "Normal user is not able to insert or query"
      )

    (-> session-user
        (request (str base-uri "/bulk-insert")
                 :headers {:bulk true}
                 :request-method :patch
                 :body (j/write-value-as-string entries))
        (ltu/body->edn)
        (ltu/is-status 403))

    (-> session-user
        (content-type "application/x-www-form-urlencoded")
        (request base-uri
                 :request-method :put
                 :body (rc/form-encode
                         {:last 0
                          :tsds-aggregation
                          (j/write-value-as-string
                            {:aggregations
                             {:tsds-stats
                              {:date_histogram
                               {:field          "@timestamp"
                                :fixed_interval "1d"}
                               :aggregations
                               {:avg-ram {:avg {:field :ram.used}}}}}})}))
        (ltu/body->edn)
        (ltu/is-status 403))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[resource-uri :put]
                            [resource-uri :post]])))
