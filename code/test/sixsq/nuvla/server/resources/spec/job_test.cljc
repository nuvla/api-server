(ns sixsq.nuvla.server.resources.spec.job-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.job :as sj]
    [sixsq.nuvla.server.resources.spec.job :as job]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-job
  (let [timestamp "1964-08-25T10:00:00.00Z"
        job       {:id                 (str sj/resource-type "/test-job")
                   :resource-type      sj/resource-type
                   :created            timestamp
                   :updated            timestamp
                   :acl                valid-acl
                   :state              "QUEUED"
                   :progress           0
                   :action             "add"
                   :started            timestamp
                   :duration           30
                   :expiry             timestamp
                   :target-resource    {:href "abc/def"}
                   :affected-resources [{:href "abc/def"}]
                   :execution-mode     "mixed"
                   :version            1}]

    (stu/is-valid ::job/schema job)

    (stu/is-valid ::job/schema (assoc job :parent-job "job/id"))
    (stu/is-valid ::job/schema (assoc job :state "RUNNING"))
    (stu/is-valid ::job/schema (assoc job :return-code 10000))
    (stu/is-valid ::job/schema (assoc job :progress 100))
    (stu/is-invalid ::job/schema (assoc job :priority 1000))
    (stu/is-valid ::job/schema (assoc job :priority 90))

    (stu/is-invalid ::job/schema (assoc job :progress 101))
    (stu/is-invalid ::job/schema (assoc job :state "XY"))
    (stu/is-invalid ::job/schema (assoc job :execution-mode "none"))
    (stu/is-invalid ::job/schema (assoc job :parent-job "notjob/id"))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :state :progress :action}]
      (stu/is-invalid ::job/schema (dissoc job k)))

    ;; optional keywords
    (doseq [k #{:target-resource :affected-resources :started :duration :expiry}]
      (stu/is-valid ::job/schema (dissoc job k)))))
