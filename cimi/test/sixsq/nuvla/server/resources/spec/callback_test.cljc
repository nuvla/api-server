(ns sixsq.nuvla.server.resources.spec.callback-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.callback :as t]
    [sixsq.nuvla.server.resources.spec.callback :as callback]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ADMIN"
                         :type      "ROLE"
                         :right     "MODIFY"}]})


(deftest check-callback-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        callback {:id             (str t/resource-url "/test-callback")
                  :resource-type    t/resource-uri
                  :created        timestamp
                  :updated        timestamp
                  :acl            valid-acl
                  :action         "validate-something"
                  :state          "WAITING"
                  :targetResource {:href "email/1230958abdef"}
                  :expires        timestamp
                  :data           {:some    "value"
                                   :another "value"}}]

    (stu/is-valid ::callback/schema callback)
    (stu/is-valid ::callback/schema (assoc callback :state "SUCCEEDED"))
    (stu/is-valid ::callback/schema (assoc callback :state "FAILED"))
    (stu/is-invalid ::callback/schema (assoc callback :state "UNKNOWN"))

    (doseq [attr #{:id :resource-type :created :updated :acl :action :state}]
      (stu/is-invalid ::callback/schema (dissoc callback attr)))

    (doseq [attr #{:targetResource :expires :data}]
      (stu/is-valid ::callback/schema (dissoc callback attr)))))
