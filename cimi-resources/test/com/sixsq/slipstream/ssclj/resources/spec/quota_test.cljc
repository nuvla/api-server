(ns com.sixsq.slipstream.ssclj.resources.spec.quota-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.quota :as sq]
    [com.sixsq.slipstream.ssclj.resources.spec.quota :as quota]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "VIEW"}]})


(deftest check-quota
  (let [timestamp "1964-08-25T10:00:00.0Z"
        quota {:id          (str sq/resource-url "/test-quota")
               :name        "Test Quota"
               :description "An example quota with a value."
               :resourceURI sq/resource-uri
               :created     timestamp
               :updated     timestamp
               :acl         valid-acl

               :resource    "VirtualMachine"
               :selection   "organization='cern'"
               :aggregation "count:id"
               :limit       100}]

    (stu/is-valid ::quota/quota quota)

    (stu/is-invalid ::quota/quota (assoc quota :resource 0))
    (stu/is-invalid ::quota/quota (assoc quota :resource ""))

    (stu/is-invalid ::quota/quota (assoc quota :selection 0))
    (stu/is-invalid ::quota/quota (assoc quota :selection ""))
    (stu/is-invalid ::quota/quota (assoc quota :selection "BAD==='FILTER'"))

    (stu/is-invalid ::quota/quota (assoc quota :aggregation 0))
    (stu/is-invalid ::quota/quota (assoc quota :aggregation ""))

    (stu/is-valid ::quota/quota (assoc quota :limit 0))
    (stu/is-invalid ::quota/quota (assoc quota :limit -1))
    (stu/is-invalid ::quota/quota (assoc quota :limit ""))


    ;; mandatory keywords
    (doseq [k #{:id :resourceURI :created :updated :acl :resource :selection :aggregation :limit}]
      (stu/is-invalid ::quota/quota (dissoc quota k)))

    ;; optional keywords
    (doseq [k #{:name :description}]
      (stu/is-valid ::quota/quota (dissoc quota k)))))
