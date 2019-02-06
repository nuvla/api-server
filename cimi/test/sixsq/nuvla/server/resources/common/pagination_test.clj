(ns sixsq.nuvla.server.resources.common.pagination-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.common.pagination :refer :all]))

(def one-to-ten
  (range 1 11))

(deftest outside-bounds
  (is (= [] (paginate 5 2 one-to-ten)))
  (is (= [] (paginate 20 30 one-to-ten))))

(deftest nominal-cases
  (is (= [1] (paginate 1 1 one-to-ten)))
  (is (= [3 4 5] (paginate 3 5 one-to-ten)))
  (is (= one-to-ten (paginate 1 10 one-to-ten)))
  (is (= one-to-ten (paginate 1 100 one-to-ten))))

(deftest partial-or-none
  (is (= one-to-ten (paginate nil nil one-to-ten)))
  (is (= [5 6 7 8 9 10] (paginate 5 nil one-to-ten)))
  (is (= [1 2] (paginate nil 2 one-to-ten)))
  (is (= [1] (paginate nil 1 one-to-ten))))

