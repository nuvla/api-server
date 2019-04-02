(ns sixsq.nuvla.server.resources.spec.resource-metadata-action-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.resource-metadata-action :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid {:name           "my-action"
            :uri            "http://sixsq.com/slipstream/action/my-action"
            :description    "some descriptive text"
            :method         "GET"
            :input-message  "application/json"
            :output-message "text/plain"})


(deftest check-action

  ;; action

  (stu/is-valid ::spec/action valid)

  (doseq [k #{:description}]
    (stu/is-valid ::spec/action (dissoc valid k)))

  (doseq [k #{:name :uri :method :input-message :output-message}]
    (stu/is-invalid ::spec/action (dissoc valid k)))

  (stu/is-invalid ::spec/action (assoc valid :badAttribute 1))
  (stu/is-invalid ::spec/action (assoc valid :name " bad name "))
  (stu/is-invalid ::spec/action (assoc valid :method "INVALID"))
  (stu/is-invalid ::spec/action (assoc valid :input-message "bad-mime-type"))
  (stu/is-invalid ::spec/action (assoc valid :output-message "bad-mime-type"))

  ;; action vector

  (stu/is-valid ::spec/actions [valid])
  (stu/is-valid ::spec/actions [valid valid])
  (stu/is-valid ::spec/actions (list valid))
  (stu/is-invalid ::spec/actions []))
