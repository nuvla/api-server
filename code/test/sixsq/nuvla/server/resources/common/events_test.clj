(ns sixsq.nuvla.server.resources.common.events-test
  (:require [clojure.test :refer [deftest testing is]]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.events.config :as events-config]
            [sixsq.nuvla.server.resources.event :as event]
            [sixsq.nuvla.server.util.time :as time]))


(deftest events-disabled-for-event-resource
  (is (false? (events-config/events-enabled event/resource-type))))

