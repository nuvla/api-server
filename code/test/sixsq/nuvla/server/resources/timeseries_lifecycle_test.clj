(ns sixsq.nuvla.server.resources.timeseries-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.db.es.binding :as es-binding]
    [ring.util.codec :as rc]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.timeseries :as t]
    [sixsq.nuvla.server.resources.timeseries.utils :as tu]
    [sixsq.nuvla.server.util.time :as time]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))

(def dimension1 "test-dimension1")

(def metric1 "test-metric1")
(def metric2 "test-metric2")

(def query1 "test-query1")
(def query2 "test-query2")
(def aggregation1 "test-metric1-avg")

(def valid-entry {:dimensions [{:field-name dimension1
                                :field-type "keyword"}]
                  :metrics    [{:field-name  metric1
                                :field-type  "double"
                                :metric-type "gauge"}
                               {:field-name  metric2
                                :field-type  "long"
                                :metric-type "counter"
                                :optional    true}]
                  :queries    [{:query-name query1
                                :query-type "standard"
                                :query      {:aggregations [{:aggregation-name aggregation1
                                                             :aggregation-type "avg"
                                                             :field-name       metric1}]}}
                               {:query-name      query2
                                :query-type      "custom-es-query"
                                :custom-es-query {:aggregations
                                                  {:agg1 {:date_histogram
                                                          {:field          "@timestamp"
                                                           :fixed_interval "1d"
                                                           :min_doc_count  0}
                                                          :aggregations {:custom-agg {:stats {:field metric1}}}}}}}]})

(defn create-timeseries
  [session entry]
  (-> session
      (request base-uri
               :request-method :post
               :body (json/write-str entry))
      (ltu/body->edn)
      (ltu/is-status 201)
      (ltu/location)))

(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-user  (header session-anon authn-info-header
                              "user/jane user/jane group/nuvla-user group/nuvla-anon")
        ;; create timeseries
        ts-id         (create-timeseries session-user valid-entry)
        ts-url        (str p/service-context ts-id)
        ;; retrieve timeseries
        ts-response   (-> session-user
                          (request ts-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present tu/action-insert))
        ts-resource   (ltu/body ts-response)
        ts-index      (tu/resource-id->timeseries-index ts-id)
        insert-op-url (ltu/get-op-url ts-response tu/action-insert)
        now           (time/now)]
    (is (= (assoc valid-entry
             :id ts-id
             :resource-type "timeseries")
           (select-keys ts-resource [:resource-type :id :dimensions :metrics :queries])))

    (testing "No timeseries is created yet"
      (is (thrown? Exception (db/retrieve-timeseries ts-index))))

    (testing "invalid timeseries creation attempts"
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-entry :dimensions [])))
          (ltu/body->edn)
          (ltu/is-status 400))

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-entry :metrics [])))
          (ltu/body->edn)
          (ltu/is-status 400)))

    (testing "query timeseries"
      (let [query-response (-> session-user
                               (request base-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-count 1)
                               (ltu/body))]
        (is (= valid-entry (-> query-response
                               :resources
                               first
                               (select-keys [:dimensions :metrics :queries]))))))

    (testing "insert timeseries datapoint"
      (let [datapoint {:timestamp (time/to-str now)
                       dimension1 "d1-val1"
                       metric1    3.14
                       metric2    1000}]
        (testing "datapoint validation error: missing dimensions"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str (dissoc datapoint dimension1)))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/is-key-value :message "missing value for dimensions: test-dimension1")))

        (testing "datapoint validation error: missing value for mandatory metrics"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str (dissoc datapoint metric1)))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/is-key-value :message "missing value for mandatory metrics: test-metric1")))

        (testing "datapoint validation error: wrong field type provided"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str (assoc datapoint dimension1 1000)))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/is-key-value :message "a value with the wrong type was provided for field test-dimension1: 1000"))

          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str (assoc datapoint metric1 "wrong-type")))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/is-key-value :message "a value with the wrong type was provided for field test-metric1: wrong-type")))

        (testing "successful insert"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str datapoint))
              (ltu/body->edn)
              (ltu/is-status 201)))

        (testing "timeseries is now created"
          (is (some? (db/retrieve-timeseries ts-index))))

        (testing "insert same datapoint again -> conflict"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str datapoint))
              (ltu/body->edn)
              (ltu/is-status 409)))

        (testing "timestamp is not mandatory"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str (dissoc datapoint :timestamp)))
              (ltu/body->edn)
              (ltu/is-status 201)))

        (testing "optional metrics can be omitted"
          (-> session-user
              (request insert-op-url
                       :request-method :post
                       :body (json/write-str (dissoc datapoint :timestamp metric2)))
              (ltu/body->edn)
              (ltu/is-status 201)))))

    (testing "bulk insert timeseries datapoints"
      (let [datapoints         [{:timestamp (time/to-str now)
                                 dimension1 "d1-val2"
                                 metric1    10
                                 metric2    1}
                                {:timestamp (time/to-str now)
                                 dimension1 "d1-val3"
                                 metric1    20
                                 metric2    2}]
            bulk-insert-op-url (ltu/get-op-url ts-response tu/action-bulk-insert)]
        (testing "missing bulk header"
          (-> session-user
              (request bulk-insert-op-url
                       :request-method :post
                       :body (json/write-str datapoints))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/is-key-value :message "Bulk request should contain bulk http header.")))

        (testing "successful bulk insert"
          (-> session-user
              (request bulk-insert-op-url
                       :headers {"bulk" true}
                       :request-method :post
                       :body (json/write-str datapoints))
              (ltu/body->edn)
              (ltu/is-status 200)))

        (testing "timestamp is not mandatory"
          (-> session-user
              (request bulk-insert-op-url
                       :headers {"bulk" true}
                       :request-method :post
                       :body (json/write-str (map #(dissoc % :timestamp) datapoints)))
              (ltu/body->edn)
              (ltu/is-status 200)))))

    (testing "update timeseries"
      (let [dimension2 "test-dimension2"
            metric3    "test-metric3"]
        (testing "removing existing dimensions is not allowed"
          (let [nok-entry {:dimensions [{:field-name dimension2
                                         :field-type "keyword"}]
                           :metrics    [{:field-name  metric1
                                         :field-type  "double"
                                         :metric-type "gauge"}
                                        {:field-name  metric2
                                         :field-type  "long"
                                         :metric-type "counter"
                                         :optional    true}]}]
            (-> session-user
                (request ts-url
                         :request-method :put
                         :body (json/write-str nok-entry))
                (ltu/body->edn)
                (ltu/is-status 400)
                (ltu/is-key-value :message "dimensions can only be appended"))))

        (testing "removing existing metrics is not allowed"
          (let [nok-entry {:dimensions [{:field-name dimension1
                                         :field-type "keyword"}]
                           :metrics    [{:field-name  metric1
                                         :field-type  "double"
                                         :metric-type "gauge"}
                                        {:field-name  metric3
                                         :field-type  "double"
                                         :metric-type "gauge"}]}]
            (-> session-user
                (request ts-url
                         :request-method :put
                         :body (json/write-str nok-entry))
                (ltu/body->edn)
                (ltu/is-status 400)
                (ltu/is-key-value :message "metrics can only be added"))))

        (testing "successful update - additional dimension and additional metric"
          (let [updated-entry {:dimensions [{:field-name dimension1
                                             :field-type "keyword"}
                                            {:field-name dimension2
                                             :field-type "keyword"}]
                               :metrics    [{:field-name  metric1
                                             :field-type  "double"
                                             :metric-type "gauge"}
                                            {:field-name  metric2
                                             :field-type  "long"
                                             :metric-type "counter"
                                             :optional    true}
                                            {:field-name  metric3
                                             :field-type  "double"
                                             :metric-type "gauge"}]}]
            (-> session-user
                (request ts-url
                         :request-method :put
                         :body (json/write-str updated-entry))
                (ltu/body->edn)
                (ltu/is-status 200))

            (testing "check that the timestream mapping has been updated with the new metric"
              (let [es-client (ltu/es-client)]
                (is (= {:time_series_metric "gauge"
                        :type               "double"}
                       (-> (es-binding/datastream-mappings es-client ts-index)
                           (get (keyword metric3)))))))

            (testing "insert datapoint with updated schema"
              (let [datapoint {:timestamp (time/now-str)
                               dimension1 "d1-val1"
                               dimension2 "d2-val1"
                               metric1    3.14
                               metric2    1000
                               metric3    12.34}]
                (-> session-user
                    (request insert-op-url
                             :request-method :post
                             :body (json/write-str datapoint))
                    (ltu/body->edn)
                    (ltu/is-status 201))))

            (testing "changing the order of existing dimensions is not allowed"
              (let [nok-entry (assoc updated-entry :dimensions
                                                   [{:field-name dimension2
                                                     :field-type "keyword"}
                                                    {:field-name dimension1
                                                     :field-type "keyword"}])]
                (-> session-user
                    (request ts-url
                             :request-method :put
                             :body (json/write-str nok-entry))
                    (ltu/body->edn)
                    (ltu/is-status 400)
                    (ltu/is-key-value :message "dimensions can only be appended"))))))))

    (testing "delete timeseries"
      (-> session-user
          (request ts-url :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; timeseries meta doc is deleted
      (-> session-user
          (request ts-url)
          (ltu/body->edn)
          (ltu/is-status 404))

      ;; timeseries is also deleted
      (is (thrown? Exception (db/retrieve-timeseries ts-index))))))

(deftest query
  (let [session-anon       (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
        session-user       (header session-anon authn-info-header
                                   "user/jane user/jane group/nuvla-user group/nuvla-anon")
        ts-id              (create-timeseries session-user valid-entry)
        ts-url             (str p/service-context ts-id)
        ;; retrieve timeseries
        ts-response        (-> session-user
                               (request ts-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-operation-present tu/action-insert))
        bulk-insert-op-url (ltu/get-op-url ts-response tu/action-bulk-insert)
        data-op-url        (ltu/get-op-url ts-response tu/action-data)

        now                (time/now)
        now-1h             (time/minus now (time/duration-unit 1 :hours))
        d1-val1            "d1q-val1"
        d1-val2            "d1q-val2"
        datapoints         [{:timestamp (time/to-str now-1h)
                             dimension1 d1-val1
                             metric1    10.0
                             metric2    1}
                            {:timestamp (time/to-str now-1h)
                             dimension1 d1-val2
                             metric1    20.0
                             metric2    2}]]

    (testing "successful bulk insert"
      (-> session-user
          (request bulk-insert-op-url
                   :headers {"bulk" true}
                   :request-method :post
                   :body (json/write-str datapoints))
          (ltu/body->edn)
          (ltu/is-status 200)))

    (ltu/refresh-es-indices)

    (testing "Query metrics"
      (let [midnight-today     (time/truncated-to-days now)
            midnight-yesterday (time/truncated-to-days (time/minus now (time/duration-unit 1 :days)))
            metrics-request    (fn [{:keys [dimensions-filters queries from from-str to to-str granularity accept-header]}]
                                 (-> session-user
                                     (content-type "application/x-www-form-urlencoded")
                                     (cond-> accept-header (header "accept" accept-header))
                                     (request data-op-url
                                              :body (rc/form-encode
                                                      (cond->
                                                        {:query queries
                                                         :from  (if from (time/to-str from) from-str)
                                                         :to    (if to (time/to-str to) to-str)}
                                                        dimensions-filters (assoc :dimension-filter dimensions-filters)
                                                        granularity (assoc :granularity granularity))))))]
        (testing "basic query"
          (let [from        (time/minus now (time/duration-unit 1 :days))
                to          now
                metric-data (-> (metrics-request {:queries     [query1]
                                                  :from        from
                                                  :to          to
                                                  :granularity "1-days"})
                                (ltu/is-status 200)
                                (ltu/body->edn)
                                (ltu/body))]
            (is (= [{:dimensions {(keyword dimension1) "all"}
                     :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                   :doc-count    0
                                   :aggregations {(keyword aggregation1) {:value nil}}}
                                  {:timestamp    (time/to-str midnight-today)
                                   :doc-count    2
                                   :aggregations {(keyword aggregation1) {:value 15.0}}}]}]
                   (get metric-data (keyword query1))))))
        (testing "basic query with dimension filter"
          (let [from        (time/minus now (time/duration-unit 1 :days))
                to          now
                metric-data (-> (metrics-request {:dimensions-filters [(str dimension1 "=" d1-val1)]
                                                  :queries            [query1]
                                                  :from               from
                                                  :to                 to
                                                  :granularity        "1-days"})
                                (ltu/is-status 200)
                                (ltu/body->edn)
                                (ltu/body))]
            (is (= [{:dimensions {(keyword dimension1) d1-val1}
                     :ts-data    [{:timestamp    (time/to-str midnight-yesterday)
                                   :doc-count    0
                                   :aggregations {(keyword aggregation1) {:value nil}}}
                                  {:timestamp    (time/to-str midnight-today)
                                   :doc-count    1
                                   :aggregations {(keyword aggregation1) {:value 10.0}}}]}]
                   (get metric-data (keyword query1))))))
        (testing "basic query with wrong dimension filter"
          (let [from (time/minus now (time/duration-unit 1 :days))
                to   now]
            (-> (metrics-request {:dimensions-filters ["wrong-dimension=w1"
                                                       "wrong-dimension=w2"]
                                  :queries            [query1]
                                  :from               from
                                  :to                 to
                                  :granularity        "1-days"})
                (ltu/body->edn)
                (ltu/is-status 400)
                (ltu/is-key-value :message "invalid dimensions: wrong-dimension"))))
        (testing "raw query"
          (let [from        (time/minus now (time/duration-unit 1 :days))
                to          now
                metric-data (-> (metrics-request {:queries     [query1]
                                                  :from        from
                                                  :to          to
                                                  :granularity "raw"})
                                (ltu/is-status 200)
                                (ltu/body->edn)
                                (ltu/body))]
            (is (= [{:dimensions {(keyword dimension1) "all"}
                     :ts-data    (set (map #(update-keys % keyword) datapoints))}]
                   (-> (get metric-data (keyword query1))
                       (update-in [0 :ts-data] set))))))
        #_(testing "custom es query"
            (let [from        (time/minus now (time/duration-unit 1 :days))
                  to          now
                  metric-data (-> (metrics-request {:queries     [query2]
                                                    :from        from
                                                    :to          to
                                                    :granularity "1-days"})
                                  (ltu/is-status 200)
                                  (ltu/body->edn)
                                  (ltu/body))]
              (is (= [{:dimensions {(keyword dimension1) "all"}
                       :ts-data    (set (map #(update-keys % keyword) datapoints))}]
                     (-> (get metric-data (keyword query1))
                         (update-in [0 :ts-data] set))))))))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[resource-uri :post]])))
