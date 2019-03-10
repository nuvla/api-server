(ns sixsq.nuvla.server.resources.spec.event-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.event :refer :all]
    [sixsq.nuvla.server.resources.spec.event :as event]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def event-timestamp "2015-01-16T08:05:00.0Z")


(def valid-event
  {:id            "event/262626262626262"
   :resource-type resource-type
   :acl           {:owner {:type "USER" :principal "joe"}
                   :rules [{:type "ROLE" :principal "ANON" :right "ALL"}]}

   :timestamp     event-timestamp
   :content       {:resource {:href "module/HNSciCloud-RHEA/S3"}
                   :state    "Started"}
   :type          "state"
   :severity      "critical"})


(deftest check-reference
  (let [updated-event (assoc-in valid-event [:content :resource :href] "another/valid-identifier")]
    (stu/is-valid ::event/event updated-event))
  (let [updated-event (assoc-in valid-event [:content :resource :href] "/not a valid reference/")]
    (stu/is-invalid ::event/event updated-event)))


(deftest check-severity
  (doseq [valid-severity ["critical" "high" "medium" "low"]]
    (stu/is-valid ::event/event (assoc valid-event :severity valid-severity)))
  (stu/is-invalid ::event/event (assoc valid-event :severity "unknown-severity")))


(deftest check-type
  (doseq [valid-type ["state" "alarm"]]
    (stu/is-valid ::event/event (assoc valid-event :type valid-type)))
  (stu/is-invalid ::event/event (assoc valid-event :type "unknown-type")))


(deftest check-event-schema

  (stu/is-valid ::event/event valid-event)

  ;; mandatory keywords
  (doseq [k #{:id :resource-type :acl :timestamp :content :type :severity}]
    (stu/is-invalid ::event/event (dissoc valid-event k)))

  ;; optional keywords
  (doseq [k #{}]
    (stu/is-valid ::event/event (dissoc valid-event k))))
