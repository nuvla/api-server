(ns com.sixsq.nuvla.server.resources.resource-metadata-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]))


(deftest check-timestamp-update
  (let [{:keys [created updated]} (->> {:created "BAD", :updated "BAD"}
                                       (md/complete-resource "random-identifier"))]

    (is (and created (not= "BAD" created)))
    (is (and updated (not= "BAD" updated)))))

