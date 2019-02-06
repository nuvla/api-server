(ns sixsq.nuvla.db.es.common.pagination-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.db.es.common.pagination :as t]))

(deftest check-es-paging-params
  (are [args expected] (= expected (apply t/es-paging-params args))
                       [1 1] [0 1]                          ;; first value only
                       [1 10] [0 10]                        ;; normal range
                       [10 10] [9 1]                        ;; single value
                       [1 t/max-size] [0 t/max-size]        ;; maximum size
                       [100 0] [99 0]                       ;; zero last -> zero size, always
                       [10 1] [9 0]                         ;; last smaller than first
                       [-1 10] [0 10]                       ;; invalid first defaults to 1
                       [nil nil] [0 t/max-size]             ;; default values when missing args
                       [nil 10] [0 10]                      ;; missing first value
                       [3 nil] [2 t/max-size]               ;; missing last value

                       [0 1] [0 1]                          ;; invalid first defaults to 1
                       [1 0] [0 0]                          ;; zero last -> zero size, always
                       [1 -1] [0 0]                         ;; invalid last value
                       )

  (is (thrown-with-msg? IllegalArgumentException #".*too large.*" (t/es-paging-params 1 (inc t/max-size)))))
