(ns sixsq.nuvla.server.resources.ts-nuvlaedge-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.ts-nuvlaedge :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.time :as time]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest insert
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
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
                 :body (json/write-str entries))
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
                     :body (json/write-str [invalid-entry]))
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
                     :body (json/write-str conflicting-entries))
            (ltu/body->edn)
            (ltu/is-status 500))))

    ;; query with the ts-aggregation trick (to be changed later on)
    ;; 1 query for cpu
    ;; 1 query for mem
    ;; 1 query per disk aggregating over all devices
    ;; 1 query per disk grouping by device
    ;; queris for network





    #_(-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str {:nuvlaedge-id "nuvlabox/1"
                                          "@timestamp"  (time/now-str)
                                          :load         1.0}))
          (ltu/body->edn)
          (ltu/is-status 201))

    #_(ltu/refresh-es-indices)

    ;; admin query succeeds but is empty
    #_(-> session-admin
          (content-type "application/x-www-form-urlencoded")
          (request base-uri
                   :request-method :put
                   :body (rc/form-encode
                           {:tsds-aggregation
                            (json/write-str
                              {:aggregations
                               {:tsds-stats
                                {:date_histogram
                                 {:field          "@timestamp"
                                  :fixed_interval "10m"}
                                 :aggregations
                                 {:load {:avg {:field :load}}}}}})}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit))

    ;;; user query succeeds but is empty
    ;(-> session-user
    ;    (request base-uri)
    ;    (ltu/body->edn)
    ;    (ltu/is-count zero?)
    ;    (ltu/is-operation-present :add)
    ;    (ltu/is-operation-absent :delete)
    ;    (ltu/is-operation-absent :edit))
    ;
    ;;; anon query fails
    ;(-> session-anon
    ;    (request base-uri)
    ;    (ltu/body->edn)
    ;    (ltu/is-status 403))
    ;
    ;;; anon create must fail
    ;(-> session-anon
    ;    (request base-uri
    ;             :request-method :post
    ;             :body (json/write-str {:address "anon@example.com"}))
    ;    (ltu/body->edn)
    ;    (ltu/is-status 403))
    ;
    ;;; check email creation
    ;(let [admin-uri     (-> session-admin
    ;                        (request base-uri
    ;                                 :request-method :post
    ;                                 :body (json/write-str {:address   "admin@example.com"
    ;                                                        :validated true}))
    ;                        (ltu/body->edn)
    ;                        (ltu/is-status 201)
    ;                        (ltu/location))
    ;      admin-abs-uri (str p/service-context admin-uri)
    ;
    ;      user-uri      (-> session-user
    ;                        (request base-uri
    ;                                 :request-method :post
    ;                                 :body (json/write-str {:address   "user@example.com"
    ;                                                        :validated true}))
    ;                        (ltu/body->edn)
    ;                        (ltu/is-status 201)
    ;                        (ltu/location))
    ;      user-abs-uri  (str p/service-context user-uri)]
    ;
    ;  ;; admin should see 2 email resources
    ;  (-> session-admin
    ;      (request base-uri)
    ;      (ltu/body->edn)
    ;      (ltu/is-status 200)
    ;      (ltu/is-resource-uri t/collection-type)
    ;      (ltu/is-count 2))
    ;
    ;  ;; user should see only 1
    ;  (-> session-user
    ;      (request base-uri)
    ;      (ltu/body->edn)
    ;      (ltu/is-status 200)
    ;      (ltu/is-resource-uri t/collection-type)
    ;      (ltu/is-count 1))
    ;
    ;  ;; verify contents of admin email
    ;  (let [email        (-> session-admin
    ;                         (request admin-abs-uri)
    ;                         (ltu/body->edn)
    ;                         (ltu/is-status 200)
    ;                         (ltu/is-operation-absent :edit)
    ;                         (ltu/is-operation-present :delete)
    ;                         (ltu/is-operation-present :validate)
    ;                         (ltu/body))
    ;        validate-url (->> (u/get-op email :validate)
    ;                          (str p/service-context))]
    ;    (is (= "admin@example.com" (:address email)))
    ;    (is (false? (:validated email)))
    ;    (is validate-url)
    ;
    ;    )
    ;
    ;  ;; verify contents of user email
    ;  (let [email        (-> session-user
    ;                         (request user-abs-uri)
    ;                         (ltu/body->edn)
    ;                         (ltu/is-status 200)
    ;                         (ltu/is-operation-absent :edit)
    ;                         (ltu/is-operation-present :delete)
    ;                         (ltu/is-operation-present :validate)
    ;                         (ltu/body))
    ;        validate-url (->> (u/get-op email "validate")
    ;                          (str p/service-context))]
    ;    (is (= "user@example.com" (:address email)))
    ;    (is (false? (:validated email)))
    ;    (is validate-url))
    ;
    ;  ;; admin can delete the email
    ;  (-> session-admin
    ;      (request admin-abs-uri :request-method :delete)
    ;      (ltu/body->edn)
    ;      (ltu/is-status 200))
    ;
    ;  ;; user can delete the email
    ;  (-> session-user
    ;      (request user-abs-uri :request-method :delete)
    ;      (ltu/body->edn)
    ;      (ltu/is-status 200))
    ))

(deftest query-ram
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
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
                 :body (json/write-str entries))
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
                                       (json/write-str
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
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
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
                 :body (json/write-str entries))
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
                            (json/write-str
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
          #_(ltu/is-key-value (comp :value :avg-ram first :buckets :tsds-stats)
                            :aggregations
                            1380.0)
          (ltu/is-count 7))

      (let [body (-> session-admin
                     (content-type "application/x-www-form-urlencoded")
                     (request base-uri
                              :request-method :put
                              :body (rc/form-encode
                                      {:last 0
                                       :tsds-aggregation
                                       (json/write-str
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
        #_(is (= (map (fn [{:keys [timestamp ram]}]
                      {:key_as_string timestamp
                       :doc_count     1
                       :avg-ram       {:value (double (:used ram))}})
                    entries)
               (->> body :aggregations :tsds-stats :buckets
                    (map #(dissoc % :key))))))

      (let [body (-> session-admin
                     (content-type "application/x-www-form-urlencoded")
                     (request base-uri
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
                                            {:stats-bytes-tx {:extended_stats {:field :network.bytes-transmitted}}
                                             :stats-bytes-rx {:extended_stats {:field :network.bytes-received}}}}}})}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/is-count 7)
                     (ltu/body))]
        #_(is (= (map (fn [{:keys [timestamp ram]}]
                        {:key_as_string timestamp
                         :doc_count     1
                         :avg-ram       {:value (double (:used ram))}})
                      entries)
                 (->> body :aggregations :tsds-stats :buckets
                      (map #(dissoc % :key))))))

      (let [body (-> session-admin
                     (content-type "application/x-www-form-urlencoded")
                     (request base-uri
                              :request-method :put
                              :body (rc/form-encode
                                      {:last 0
                                       :tsds-aggregation
                                       (json/write-str
                                         {:aggregations
                                          {:tsds-stats
                                           {:date_histogram
                                            {:field          "@timestamp"
                                             :fixed_interval "30s"}
                                            :aggregations
                                            {:max-tx {:max {:field :network.bytes-transmitted}}
                                             :rate-tx {:derivative {:buckets_path "max-tx"}}
                                             :only-pos-rate-tx {:bucket_script
                                                                {:buckets_path {:rateTx "rate-tx"}
                                                                 :script       "if (params.rateTx > 0) { return params.rateTx } else { return null }"}}}}}})}))
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/is-count 7)
                     (ltu/body))]
        #_(is (= (map (fn [{:keys [timestamp ram]}]
                        {:key_as_string timestamp
                         :doc_count     1
                         :avg-ram       {:value (double (:used ram))}})
                      entries)
                 (->> body :aggregations :tsds-stats :buckets
                      (map (comp :value :only-pos-rate-tx)))))))))

;; TODO: add test that normal user is not able to insert or query

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[resource-uri :put]
                            [resource-uri :post]])))
