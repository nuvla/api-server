(ns sixsq.nuvla.server.resources.common.schema-test
  (:require
    [clojure.set :as set]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.common.schema :refer :all]))

(deftest check-actions
  (is (= (set/union core-actions prefixed-actions impl-prefixed-actions) (set (keys action-uri))))
  (is (= (set (map name core-actions)) (set (vals (select-keys action-uri core-actions))))))

