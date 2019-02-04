(ns com.sixsq.slipstream.util.convert-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.util.convert :as c])
  (:import
    [java.util ArrayList HashMap]))


(deftest walk-clojurify-should-recursively-keywordize-a-java-map
  (is (=
        {:A 42 :c {:B [{:Z 111} {:e 1515}]}}
        (c/walk-clojurify
          (HashMap. {"A" 42 "c" {"B" (ArrayList. [(HashMap. {"Z" 111}) {"e" 1515}])}})))))
