(ns com.sixsq.slipstream.dbtest.es.order-test
  (:refer-clojure :exclude [read update])
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.db.es.order :as t]
    [com.sixsq.slipstream.db.es.utils :as eu]
    [com.sixsq.slipstream.dbtest.es.utils :as eut])
  (:import (org.elasticsearch.action.search SearchRequestBuilder)
           (org.elasticsearch.search SearchHit SearchHits)
           (org.elasticsearch.search.sort SortOrder)))

(deftest check-direction->sort-order
  (is (= SortOrder/ASC (t/direction->sort-order :asc)))
  (is (= SortOrder/DESC (t/direction->sort-order :desc)))
  (is (thrown-with-msg? IllegalArgumentException #".*invalid.*:unknown.*" (t/direction->sort-order :unknown))))

(defn test-search-results [client index type n order test-fn]
  (let [result (eu/search client index type {:user-name   "admin"
                                             :cimi-params {:first   1
                                                           :last    (* 2 n)
                                                           :orderby [["number" order]]}})
        total (-> result :hits :total)
        docs (map :_source (-> result :hits :hits))]

    (is (= n total))
    (is (= n (count docs)))
    (is (apply test-fn (map :number docs)))))

(deftest check-sorting-in-search
  (eut/with-es-test-client
    (let [n 10
          type "test-resource"
          shuffled (shuffle
                     (doall
                       (for [n (range n)]
                         [(eu/random-index-name)
                          (eu/edn->json {:_acl-users ["admin"], :number n})])))]

      ;; insert generated records in random order
      (is (not (.hasFailures (eu/bulk-create client index type shuffled))))

      ;; check that searches with ordering both work correctly
      (test-search-results client index type n :asc <)
      (test-search-results client index type n :desc >))))
