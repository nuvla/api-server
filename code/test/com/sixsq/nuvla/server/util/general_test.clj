(ns com.sixsq.nuvla.server.util.general-test
  (:require
    [clojure.test :refer [are deftest]]
    [com.sixsq.nuvla.server.util.general :as t]))

(deftest filter-map-nil-value
  (are [expect arg] (= expect (t/filter-map-nil-value arg))
                    {} nil
                    {} {}
                    {:a 1} {:a 1}
                    {:a 1 :b "b"} {:a 1 :b "b"}
                    {:a 1 :c {} :d false} {:d false :a 1 :b nil :c {}}))


(deftest merge-and-ignore-input-immutable-attrs
  (are [expect arg-map]
    (= expect (t/merge-and-ignore-input-immutable-attrs
                (:input arg-map)
                (:origin arg-map)
                (:attrs arg-map)))
    {} {:origin {} :input {} :attrs []}
    {} {:origin {} :input {}}
    {} {:origin nil :input {}}
    nil {:origin nil :input nil}
    {:a 1} {:origin {:a 1} :input nil}
    {:a 1} {:origin {:a 1} :input {} :attrs [:a]}
    {:a 1} {:origin {:a 1} :input {:a 2} :attrs [:a]}
    {:a 1 :b 3} {:origin {:a 1 :b 2} :input {:a 2 :b 3} :attrs [:a]}
    {:a {:nested "something"} :b 3} {:origin {:a {:nested "something"} :b 2} :input {:a 2 :b 3} :attrs [:a]}
    {:a {:nested "something"} :b 2 :c 3} {:origin {:a {:nested "something"} :b 2 :c 3} :input {:a 2 :b 3} :attrs [:a :b]}))

(deftest encode-uri-component
  (are [expect input]
    (= expect (t/encode-uri-component input))
    "abc(def)" "abc(def)"
    "hello%20world!" "hello world!"
    "a%2Bb%40gmail.com" "a+b@gmail.com"
    "a%2B%2Bb%40gmail.com" "a++b@gmail.com"
    "https%3A%2F%2Fnuvla.io%2Fapi%2Fcallback%2F65b34372-6814-47aa-9869-510e38315db1%2Fexecute" "https://nuvla.io/api/callback/65b34372-6814-47aa-9869-510e38315db1/execute"
    "!~%2B()'" "!~+()'"))

(deftest decode-uri-component
  (are [expect input]
    (= expect (t/decode-uri-component input))
    "abc(def)" "abc(def)"
    "hello world!" "hello%20world!"
    "a+b@gmail.com" "a%2Bb%40gmail.com"
    "a++b@gmail.com" "a%2B%2Bb%40gmail.com"
    "https://nuvla.io/api/callback/65b34372-6814-47aa-9869-510e38315db1/execute" "https%3A%2F%2Fnuvla.io%2Fapi%2Fcallback%2F65b34372-6814-47aa-9869-510e38315db1%2Fexecute"
    "!~+()'" "!~%2B()'"))
