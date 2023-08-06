(ns sixsq.nuvla.server.resources.event-utils-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.eventing :as eventing]
    [sixsq.nuvla.server.resources.event.utils :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.time :as time]))


(use-fixtures :each ltu/with-test-server-fixture)

(deftest search-event
  (doseq [event-type ["user.create.requested" "user.create.completed"]
          timestamp ["2015-01-16T08:05:00.000Z" "2015-01-17T08:05:00.000Z" (time/now-str)]]
    (eventing/create-event*
      {:nuvla/authn auth/internal-identity}
      "user/1"
      {:event-type event-type
       :acl        {:owners ["group/nuvla-admin"]}
       :timestamp  timestamp}))
  (is (= 6 (count (t/search-event "user/1" {}))))
  (is (= 0 (count (t/search-event "user/2" {}))))
  (is (= 3 (count (t/search-event "user/1" {:category "command"}))))
  (is (= 3 (count (t/search-event "user/1" {:category "crud"}))))
  (is (= 3 (count (t/search-event "user/1" {:event-type "user.create.requested"}))))
  (is (= 3 (count (t/search-event "user/1" {:event-type "user.create.completed"}))))
  (is (= 6 (count (t/search-event "user/1" {:start "2015-01-16T08:05:00.000Z"}))))
  (is (= 2 (count (t/search-event "user/1" {:end "2015-01-16T08:06:00.000Z"}))))
  (is (= 1 (count (t/search-event "user/1" {:category "command"
                                            :start    "now/d" :end "now+1d/d"})))))
