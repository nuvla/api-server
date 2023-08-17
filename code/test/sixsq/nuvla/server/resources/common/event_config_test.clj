(ns sixsq.nuvla.server.resources.common.event-config-test
  (:require [clojure.test :refer [deftest is]]
            [sixsq.nuvla.server.resources.common.event-config :as t]))


(def logged-event {:event-type "resource.add"})


(def not-logged-event {})


(def disabled-event {:event-type "resource.validate"})


(defmethod t/log-event?
  "resource.validate"
  [_event _response]
  false)


(deftest log-event
  (is (true? (t/log-event? logged-event {})))
  (is (false? (t/log-event? not-logged-event {})))
  (is (false? (t/log-event? disabled-event {})))
  (is (false? (t/log-event? logged-event {:status 405}))))

