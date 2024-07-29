(ns com.sixsq.nuvla.server.resources.spec.event-test
  (:require
    [clojure.test :refer [deftest testing]]
    [com.sixsq.nuvla.server.resources.event :as t]
    [com.sixsq.nuvla.server.resources.spec.event :as event]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def event-timestamp "2015-01-16T08:05:00.00Z")


(def valid-event
  {:id            "event/262626262626262"
   :name          "test"
   :success       true
   :resource-type t/resource-type
   :created       event-timestamp
   :updated       event-timestamp
   :acl           {:owners   ["user/joe"]
                   :view-acl ["group/nuvla-anon"]}

   :timestamp     event-timestamp
   :content       {:resource {:href "module/HNSciCloud-RHEA/S3"}
                   :state    "Started"}
   :category      "state"
   :severity      "critical"
   :authn-info    {:user-id      "user/a978c1c0-f958-4238-9eba-aab85714b114"
                   :claims       ["group/nuvla-anon" "group/nuvla-user"]
                   :active-claim "group/nuvla-user"}})


(deftest check-reference
  (let [updated-event (assoc-in valid-event [:content :resource :href] "another/valid-identifier")]
    (stu/is-valid ::event/schema updated-event)))


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
  (doseq [k #{:id :resource-type :acl :timestamp :content :category :severity}]
    (stu/is-invalid ::event/schema (dissoc valid-event k)))

  ;; optional keywords
  (doseq [k #{}]
    (stu/is-valid ::event/schema (dissoc valid-event k))))

(deftest blackbox-event
  (testing "Make sure events sent by Blackbox app are accepted"
    (let [blackbox-event (merge (select-keys valid-event [:id :resource-type :created :updated :acl :timestamp])
                                {:category "user"
                                 :content  {:resource {:href "data_record/1234"}
                                            :state    "created"}
                                 :severity "medium"
                                 :tags     ["application/blackbox"]})]
      (stu/is-valid ::event/schema blackbox-event))))
