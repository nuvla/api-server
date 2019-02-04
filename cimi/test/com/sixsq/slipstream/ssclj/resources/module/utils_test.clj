(ns com.sixsq.slipstream.ssclj.resources.module.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.module.utils :as t]))


(deftest split-resource
  (is (= [{:alpha 1, :beta 2} {:gamma 3}]
         (t/split-resource {:alpha   1
                            :beta    2
                            :content {:gamma 3}}))))


(deftest check-parent-path
  (are [parent-path path] (= parent-path (t/get-parent-path path))
                          nil nil
                          "" "alpha"
                          "alpha" "alpha/beta"
                          "alpha/beta" "alpha/beta/gamma"))


(deftest check-set-parent-path
  (are [expected arg] (= expected (:parentPath (t/set-parent-path arg)))
                      "ok" {:parentPath "bad-value"
                            :path       "ok/go"}
                      nil {}))
