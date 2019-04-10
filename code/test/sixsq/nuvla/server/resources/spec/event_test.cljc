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
   :created       event-timestamp
   :updated       event-timestamp
   :acl           {:owners   ["user/joe"]
                   :view-acl ["group/nuvla-anon"]}

   :timestamp     event-timestamp
   :content       {:resource {:href "module/HNSciCloud-RHEA/S3"}
                   :state    "Started"}
   :type          "state"
   :severity      "critical"})


(deftest check-reference
  (let [updated-event (assoc-in valid-event [:content :resource :href] "another/valid-identifier")]
    (stu/is-valid ::event/schema updated-event))
  (let [updated-event (assoc-in valid-event [:content :resource :href] "/not a valid reference/")]
    (stu/is-invalid ::event/schema updated-event)))


(deftest check-severity
  (doseq [valid-severity ["critical" "high" "medium" "low"]]
    (stu/is-valid ::event/schema (assoc valid-event :severity valid-severity)))
  (stu/is-invalid ::event/schema (assoc valid-event :severity "unknown-severity")))


(deftest check-type
  (doseq [valid-type ["state" "alarm"]]
    (stu/is-valid ::event/schema (assoc valid-event :type valid-type)))
  (stu/is-invalid ::event/schema (assoc valid-event :type "unknown-type")))


(deftest check-event-schema

  (stu/is-valid ::event/schema valid-event)

  ;; mandatory keywords
  (doseq [k #{:id :resource-type :acl :timestamp :content :type :severity}]
    (stu/is-invalid ::event/schema (dissoc valid-event k)))

  ;; optional keywords
  (doseq [k #{}]
    (stu/is-valid ::event/schema (dissoc valid-event k))))
