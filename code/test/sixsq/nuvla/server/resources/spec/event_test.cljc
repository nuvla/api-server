(ns sixsq.nuvla.server.resources.spec.event-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.event :as t]
    [sixsq.nuvla.server.resources.spec.event :as event]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def event-timestamp "2015-01-16T08:05:00.00Z")


(def valid-event
  {:id            "event/262626262626262"
   :resource-type t/resource-type
   :created       event-timestamp
   :updated       event-timestamp
   :acl           {:owners   ["user/joe"]
                   :view-acl ["group/nuvla-anon"]}
   :event-type    "module.create"
   :timestamp     event-timestamp
   :resource      {:resource-type "module"
                   :href "module/HNSciCloud-RHEA/S3"}
   :active-claim  "user/joe"
   :details       {:state "Started"}
   :category      "state"
   :severity      "critical"})


(deftest check-supported-event-types
  (stu/is-valid ::event/schema valid-event)
  (let [wrong-event (assoc valid-event :event-type "unsupported.event.type")]
    (stu/is-invalid ::event/schema wrong-event)))


(deftest check-reference
  (let [updated-event (assoc valid-event :resource {:resource-type "module"
                                                    :href          "module/valid-identifier"})]
    (stu/is-valid ::event/schema updated-event))
  (let [updated-event (assoc valid-event :resource {:resource-type "module"
                                                    :href          "/not a valid reference/"})]
    (stu/is-invalid ::event/schema updated-event)))


(deftest check-severity
  (doseq [valid-severity ["critical" "high" "medium" "low"]]
    (stu/is-valid ::event/schema (assoc valid-event :severity valid-severity)))
  (stu/is-invalid ::event/schema (assoc valid-event :severity "unknown-severity")))


(deftest check-category
  (doseq [valid-category ["state" "alarm"]]
    (stu/is-valid ::event/schema (assoc valid-event :category valid-category)))
  (stu/is-invalid ::event/schema (assoc valid-event :category "unknown-category")))


(deftest check-event-schema

  (stu/is-valid ::event/schema valid-event)

  ;; mandatory keywords
  (doseq [k #{:id :resource-type :acl :event-type :timestamp :category :severity :resource :active-claim}]
    (stu/is-invalid ::event/schema (dissoc valid-event k)))

  ;; optional keywords
  (doseq [k #{}]
    (stu/is-valid ::event/schema (dissoc valid-event k))))

