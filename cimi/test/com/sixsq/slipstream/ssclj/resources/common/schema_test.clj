(ns com.sixsq.slipstream.ssclj.resources.common.schema-test
  (:require
    [clojure.set :as set]
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.common.schema :refer :all]))

(deftest check-actions
  (is (= (set/union core-actions prefixed-actions impl-prefixed-actions) (set (keys action-uri))))
  (is (= (set (map name core-actions)) (set (vals (select-keys action-uri core-actions))))))

