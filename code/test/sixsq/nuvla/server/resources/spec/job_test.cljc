(ns sixsq.nuvla.server.resources.spec.job-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.job :as sj]
    [sixsq.nuvla.server.resources.spec.job :as job]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-job
  (let [timestamp "1964-08-25T10:00:00.0Z"
        job {:id                 (str sj/resource-type "/test-job")
             :resource-type      sj/resource-type
             :created            timestamp
             :updated            timestamp
             :acl                valid-acl
             :state              "QUEUED"
             :progress           0
             :action             "add"
             :started            timestamp
             :duration           30
             :target-resource    {:href "abc/def"}
             :affected-resources [{:href "abc/def"}]}]

    (stu/is-valid ::job/job job)

    (stu/is-valid ::job/job (assoc job :parent-job "job/id"))
    (stu/is-valid ::job/job (assoc job :state "RUNNING"))
    (stu/is-valid ::job/job (assoc job :return-code 10000))
    (stu/is-valid ::job/job (assoc job :progress 100))
    (stu/is-invalid ::job/job (assoc job :priority 1000))
    (stu/is-valid ::job/job (assoc job :priority 90))

    (stu/is-invalid ::job/job (assoc job :progress 101))
    (stu/is-invalid ::job/job (assoc job :state "XY"))
    (stu/is-invalid ::job/job (assoc job :parent-job "notjob/id"))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :state :progress :action}]
      (stu/is-invalid ::job/job (dissoc job k)))

    ;; optional keywords
    (doseq [k #{:target-resource :affected-resources :started :duration}]
      (stu/is-valid ::job/job (dissoc job k)))))
