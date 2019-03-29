(ns sixsq.nuvla.server.resources.spec.evidence-record-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.evidence-record :as evidence-record]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})


(def timestamp "1964-08-25T10:00:00.0Z")


(deftest evidence-record-schema-check
  (let [root (-> {:id            "evidence-record/1234"
                  :resource-type "evidence-record"
                  :created       timestamp
                  :updated       timestamp

                  :end-time      timestamp
                  :start-time    timestamp
                  :plan-id       "b12345"
                  :passed        true
                  :class         "className"
                  :log           ["log1", "log2"]}
                 (assoc :acl valid-acl))]

    (stu/is-valid ::evidence-record/schema root)

    ;; mandatory keywords
    (doseq [k #{:end-time :passed :plan-id :start-time}]
      (stu/is-invalid ::evidence-record/schema (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:log :class}]
      (stu/is-valid ::evidence-record/schema (dissoc root k)))))
