(ns sixsq.nuvla.server.resources.timeseries-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
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


(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-user  (header session-anon authn-info-header
                              "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        dimension1    "test-dimension1"
        metric1       "test-metric1"
        metric2       "test-metric2"
        entry         {:dimensions [{:field-name dimension1
                                     :field-type "keyword"}]
                       :metrics    [{:field-name  metric1
                                     :field-type  "double"
                                     :metric-type "gauge"}
                                    {:field-name  metric2
                                     :field-type  "long"
                                     :metric-type "counter"
                                     :optional    true}]}
        ;; create timeseries
        ts-id         (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str entry))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))
        ts-url        (str p/service-context ts-id)
        ;; retrieve timeseries
        ts-response   (-> session-user
                          (request ts-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present tu/action-insert))
        ts-resource   (ltu/body ts-response)
        ts-index      (tu/resource-id->timeseries-index ts-id)
        ts            (db/retrieve-timeseries ts-index)
        insert-op-url (ltu/get-op-url ts-response tu/action-insert)
        now           (time/now)]
    (is (= (assoc entry
             :id ts-id
             :resource-type "timeseries")
           (select-keys ts-resource [:resource-type :id :dimensions :metrics])))
    (is (pos? (count (:data_streams ts))))

    (testing "query timeseries"
      (let [query-response (-> session-user
                               (request base-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-count 1)
                               (ltu/body))]
        (is (= entry (-> query-response
                         :resources
                         first
                         (select-keys [:dimensions :metrics]))))))

    (testing "insert timeseries datapoint"
      (let [datapoint     {:timestamp (time/to-str now)
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

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[resource-uri :post]])))
