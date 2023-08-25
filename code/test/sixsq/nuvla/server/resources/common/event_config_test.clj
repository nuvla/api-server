(ns sixsq.nuvla.server.resources.common.event-config-test
  (:require [clojure.test :refer [deftest is]]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.event-config :as t]))


(def logged-event {:name       "resource.add"
                   :category   "add"
                   :success    true
                   :authn-info {:user-id "user/12345"}
                   :content    {:resource {:href "resource/12345"}}})


(def not-logged-event {})


(def disabled-event {:name "resource.validate"})


(def anon-event {:name       "resource.add"
                 :category   "add"
                 :success    true
                 :authn-info {:claims ["group/nuvla-anon"]}
                 :content    {:resource {:href "resource/12345"}}})


(def failure-event {:name    "resource.add"
                    :success false})



(defmethod t/log-event?
  "resource.validate"
  [_event _response]
  false)


(deftest log-event
  (is (true? (t/log-event? logged-event {})))
  (is (false? (t/log-event? not-logged-event {})))
  (is (false? (t/log-event? disabled-event {})))
  (is (false? (t/log-event? logged-event {:status 405}))))


(deftest event-description
  (with-redefs [crud/retrieve-by-id-as-admin
                #(case %
                   "user/12345" {:name "TestUser"}
                   "resource/12345" {:resource-type "resource"
                                     :name          "TestResource"})]
    (is (= "TestUser added resource TestResource." (t/event-description logged-event)))
    (is (= "An anonymous user added resource TestResource." (t/event-description anon-event)))
    (is (= "resource.add attempt failed." (t/event-description failure-event)))))
