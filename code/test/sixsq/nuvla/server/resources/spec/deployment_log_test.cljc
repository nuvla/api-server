(ns sixsq.nuvla.server.resources.spec.deployment-log-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.deployment-log :as t]
    [sixsq.nuvla.server.resources.spec.deployment-log :as dl]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id             (str t/resource-type "/uuid")
                   :resource-type  t/resource-type
                   :parent         "deployment/7b1ad037-a65e-41e0-8fdf-e0e8db30bb0b"
                   :created        timestamp
                   :updated        timestamp
                   :acl            valid-acl

                   :service        "my-service"
                   :since          "1964-08-25T10:00:00.00Z"
                   :last-timestamp "2019-08-25T10:00:00.00Z"
                   :lines          10
                   :log            ["some\nlong\nlog\ninformation\n"]}]

    (stu/is-valid ::dl/schema root)
    (stu/is-invalid ::dl/schema (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :parent :created :updated :acl :service}]
      (stu/is-invalid ::dl/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:name :since :last-timestamp :lines :log}]
      (stu/is-valid ::dl/schema (dissoc root k)))))
