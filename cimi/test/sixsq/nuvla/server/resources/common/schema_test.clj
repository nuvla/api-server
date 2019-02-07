(ns sixsq.nuvla.server.resources.common.schema-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.common.schema :refer :all]))

(deftest check-actions
  (is (= (set (map name actions)) (set (vals (select-keys action-uri actions))))))

