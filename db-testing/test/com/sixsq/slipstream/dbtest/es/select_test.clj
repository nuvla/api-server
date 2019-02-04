(ns com.sixsq.slipstream.dbtest.es.select-test
  (:refer-clojure :exclude [read update])
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.db.es.select :as t]
    [com.sixsq.slipstream.db.es.utils :as eu]
    [com.sixsq.slipstream.dbtest.es.utils :as eut])
  (:import (org.elasticsearch.action.search SearchRequestBuilder)
           (org.elasticsearch.search.aggregations AggregationBuilder)
           (org.elasticsearch.search.sort SortOrder)))

(deftest check-select-keys
  (eut/with-es-test-client
    (let [n 10
          type "test-resource"
          shuffled (shuffle
                     (doall
                       (for [n (range n)]
                         [(eu/random-index-name)
                          (eu/edn->json {:_acl-users  ["admin"]
                                         :resourceURI "resource-uri"
                                         :number      n
                                         :doubled     (* 2 n)
                                         :x1          "x1"
                                         :x2          "x2"
                                         :acl         "acl"})])))
          uuid (ffirst shuffled)]

      ;; insert generated records in random order
      (is (not (.hasFailures (eu/bulk-create client index type shuffled))))

      ;; test search
      (let [result (eu/search client index type {:user-name   "admin"
                                                 :cimi-params {:first  1
                                                               :last   (* 2 n)
                                                               :select #{"resourceURI" "doubled" "x*"}}})

            total (-> result :hits :total)
            docs (map :_source (-> result :hits :hits))]

        (is (= n total))

        (is (every? :resourceURI docs))
        (is (every? :doubled docs))
        (is (every? :x1 docs))
        (is (every? :x2 docs))
        (is (every? :acl docs))
        (is (not-every? :number docs)))

      ;; test get
      (let [doc (-> (eu/read client index type uuid {:user-name   "admin"
                                                     :cimi-params {:select #{"resourceURI" "doubled"}}})
                    (.getSourceAsString)
                    (eu/json->edn))]

        (is (:resourceURI doc))
        (is (:doubled doc))
        (is (nil? (:x1 doc)))
        (is (nil? (:x2 doc)))
        (is (:acl doc))
        (is (nil? (:number doc)))))))
