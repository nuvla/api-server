(ns sixsq.nuvla.server.resources.common.event-context-test
  (:require [clojure.test :refer [deftest is]]
            [sixsq.nuvla.server.resources.common.event-context :as t]))


(deftest event-context
  (let [k         :test-key
        info      "something"
        linked-id "linked-identifier"]
    (t/with-context
      (is (= [:timestamp] (keys (t/get-context))))
      (t/add-to-context k info)
      (is (= info (get (t/get-context) k)))

      (t/add-linked-identifier linked-id)
      (is (some #{linked-id} (:linked-identifiers (t/get-context))))

      (t/add-to-visible-to "user/toto")
      (is (= #{"user/toto"} (set (:visible-to (t/get-context)))))
      (t/add-to-visible-to "user/tata" "user/titi")
      (is (= #{"user/tata" "user/titi" "user/toto"} (set (:visible-to (t/get-context)))))
      )))
