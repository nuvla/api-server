(ns sixsq.nuvla.server.filter.performance-parser-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [sixsq.nuvla.db.filter.parser :as parser]))

(defn cimi-filter
  [n op]
  (str/join op (repeat n "(a='1')")))

;;
;; This test ensures that the performance of the CIMI filter
;; parser is acceptable for large numbers of 'and' or 'or'
;; expressions.
;; A grammar that uses wildcards (see cimi-filter-grammar.txt) would
;; cause a memory overflow failure above 30 or so terms.
;;
(deftest test-parse-performance
  (is (parser/parse-cimi-filter (cimi-filter 1000 " or ")))
  (is (parser/parse-cimi-filter (cimi-filter 1000 " and "))))
