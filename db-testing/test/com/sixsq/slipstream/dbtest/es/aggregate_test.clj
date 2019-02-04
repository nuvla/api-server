(ns com.sixsq.slipstream.dbtest.es.aggregate-test
  (:refer-clojure :exclude [read update])
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.db.es.aggregation :as t]
    [com.sixsq.slipstream.db.es.utils :as eu]
    [com.sixsq.slipstream.dbtest.es.utils :as eut]

    )
  (:import (org.elasticsearch.action.search SearchRequestBuilder)
           (org.elasticsearch.search.aggregations AggregationBuilder)
           (org.elasticsearch.search.sort SortOrder)))

(deftest check-aggregator-constructors
  (doseq [algo (keys t/aggregator-constructors)]
    (let [algo-fn (t/aggregator-constructors algo)]
      (is (instance? AggregationBuilder (algo-fn "field-name"))))))

(deftest check-aggregation-in-search
  (eut/with-es-test-client
    (let [n 19
          type "test-resource"
          shuffled (shuffle
                     (doall
                       (for [n (range n)]
                         [(eu/random-index-name)
                          (eu/edn->json (cond-> {:_acl-users ["admin"]
                                                 :number     n
                                                 :doubled    (* 2 n)
                                                 :even-odd   (if (even? n) "even" "odd")
                                                 :nested     {:number n}}
                                                (even? n) (assoc :flag "flag")))])))
          min-value-number 0.0
          min-value-doubled 0.0

          max-value-number (double (dec n))
          max-value-doubled (double (* 2 (dec n)))

          sum-number (double (apply + (range n)))
          sum-doubled (double (* 2 (apply + (range n))))

          avg-number (/ (double (apply + (range n))) n)
          avg-doubled (/ (double (* 2 (apply + (range n)))) n)]

      ;; insert generated records in random order
      (is (not (.hasFailures (eu/bulk-create client index type shuffled))))

      (let [cimi-params {:first       1
                         :last        (* 2 n)
                         :aggregation {:min           ["number" "doubled"]
                                       :max           ["number" "doubled"]
                                       :sum           ["number" "doubled"]
                                       :avg           ["number" "doubled" "nested/number"]
                                       :stats         ["number" "doubled"]
                                       :extendedstats ["number" "doubled"]
                                       :percentiles   ["number" "doubled"]
                                       :count         ["number" "even-odd"]
                                       :cardinality   ["number" "even-odd" "flag"]
                                       :missing       ["flag"]
                                       :terms         ["even-odd" "flag"]}}

            result (eu/search client index type {:user-name   "admin"
                                                 :cimi-params cimi-params})

            total (-> result :hits :total)
            aggregations (:aggregations result)]

        (is (= n total))

        (are [path v] (= (get-in aggregations path) v)
                      [:min:number :value] min-value-number
                      [:min:doubled :value] min-value-doubled

                      [:max:number :value] max-value-number
                      [:max:doubled :value] max-value-doubled

                      [:sum:number :value] sum-number
                      [:sum:doubled :value] sum-doubled

                      [:avg:number :value] avg-number
                      [:avg:doubled :value] avg-doubled
                      [:avg:nested/number :value] avg-number

                      [:stats:number :sum] sum-number
                      [:stats:doubled :sum] sum-doubled

                      [:extendedstats:number :sum] sum-number
                      [:extendedstats:doubled :sum] sum-doubled

                      [:count:even-odd :value] n
                      [:count:number :value] n

                      [:cardinality:even-odd :value] 2
                      [:cardinality:even-odd :value] 2
                      [:cardinality:flag :value] 1
                      [:cardinality:flag :value] 1

                      [:missing:flag :doc_count] (int (/ n 2))

                      [:terms:even-odd :sum_other_doc_count] 0
                      [:terms:flag :sum_other_doc_count] 0)

        (is (get-in aggregations [:percentiles:number :values :1.0]))
        (is (get-in aggregations [:percentiles:doubled :values :1.0]))))))
