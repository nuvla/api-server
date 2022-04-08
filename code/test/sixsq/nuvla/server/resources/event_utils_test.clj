(ns sixsq.nuvla.server.resources.event-utils-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.server.resources.event.test-utils :as tu]
    [sixsq.nuvla.server.resources.event.utils :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.time :as time]))


(use-fixtures :each ltu/with-test-server-fixture)

(deftest search-event
  (doseq [category  ["action" "system"]
          timestamp ["2015-01-16T08:05:00.000Z" "2015-01-17T08:05:00.000Z" (time/now-str)]]
    (t/create-event "user/1" "hello" {:owners ["group/nuvla-admin"]}
                    :category category
                    :timestamp timestamp))
  (is (= 6 (count (t/search-event "user/1" {}))))
  (is (= 0 (count (t/search-event "user/2" {}))))
  (is (= 3 (count (t/search-event "user/1" {:category "action"}))))
  (is (= 6 (count (t/search-event "user/1" {:start "2015-01-16T08:05:00.000Z"}))))
  (is (= 2 (count (t/search-event "user/1" {:end   "2015-01-16T08:06:00.000Z"}))))
  (is (= 1 (count (t/search-event "user/1" {:category "action"
                                            :start "now/d" :end "now+1d/d"})))))
