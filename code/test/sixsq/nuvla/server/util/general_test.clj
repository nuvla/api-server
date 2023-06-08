(ns sixsq.nuvla.server.util.general-test
  (:require
    [clojure.test :refer [are deftest]]
    [sixsq.nuvla.server.util.general :as t]))

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