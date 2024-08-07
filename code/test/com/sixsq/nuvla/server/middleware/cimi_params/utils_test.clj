(ns com.sixsq.nuvla.server.middleware.cimi-params.utils-test
  (:require
    [clojure.test :refer [are deftest]]
    [com.sixsq.nuvla.server.middleware.cimi-params.utils :as t]))


(deftest check-as-vector
  (are [expect arg] (= expect (t/as-vector arg))
                    [] nil
                    [1] 1
                    ["a"] "a"
                    [1 2] '(1 2)
                    [1 2] [1 2]
                    [1 "a"] '(1 "a")))


(deftest check-as-long
  (are [expect arg] (= expect (t/as-long arg))
                    nil [1]
                    nil {:a 1}
                    nil #{1}
                    1 1
                    -1 -1
                    nil 2.5
                    nil 10/3
                    1 "1"
                    -1 "-1"
                    nil "2.5"
                    nil "10/3"))


(deftest check-first-valid-long
  (are [expect arg] (= expect (t/first-valid-long arg))
                    nil []
                    nil ["a"]
                    nil ["a" "b"]
                    1 [1]
                    1 ["1"]
                    1 ["a" 1]
                    1 ["a" 1 2]
                    1 [1 2]
                    1 ["a" 1 "c"]
                    -1 [-1]
                    -1 ["a" "-1" "c"]
                    nil [nil nil]
                    nil [{:a 1} [2]]))


(deftest check-comma-split
  (are [expect arg] (= expect (t/comma-split arg))
                    ["a" "b"] "a,b"
                    ["a" "b"] " a , b "
                    ["a" "b"] ", a , b ,"
                    [] ""
                    [] ","
                    [] nil))


(deftest check-orderby-clause
  (are [expect arg] (= expect (t/orderby-clause arg))
                    nil ":asc"
                    nil ":desc"
                    [":asc" :asc] ":asc:asc"
                    [":desc" :desc] ":desc:desc"
                    [":" :asc] ":"
                    [":a" :desc] ":a:desc"
                    ["a" :asc] "a"
                    ["a:" :asc] "a:"
                    ["a" :desc] "a:desc"
                    ["a:dummy" :asc] "a:dummy"))


(deftest check-aggregation-clause
  (are [expect arg] (= expect (t/aggregation-clause arg))
                    nil ""
                    nil "algo"
                    nil "algo:"
                    nil "algo: "
                    nil ":attr-name"
                    nil " :attr-name"
                    nil "BAD:attr-name"
                    [:sum "attr"] "sum:attr"
                    [:value_count "attr"] "value_count:attr"
                    [:extended-algo "attr"] "extended-algo:attr"
                    [:sum "attr:name"] "sum:attr:name"))

