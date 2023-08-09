(ns sixsq.nuvla.events.config-test
  (:require [clojure.test :refer [deftest is]]
            [sixsq.nuvla.events.config :as t]
            [sixsq.nuvla.events.std-events :as std-events]))


(deftest events-enabled
  (is (true? (t/events-enabled? "default")))
  (is (false? (t/events-enabled? "event"))))

(deftest supported-event-types
  (is (= (t/supported-event-types "default")
         (merge
           (std-events/crud-event-types "default")
           (std-events/actions-event-types "default")))))


(deftest events-config
  (is (some? (t/get-event-config "module" "module.create")))
  (is (nil? (t/get-event-config "module" "deployment.create"))))
