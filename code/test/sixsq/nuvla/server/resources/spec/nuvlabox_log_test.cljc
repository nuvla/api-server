(ns sixsq.nuvla.server.resources.spec.nuvlabox-log-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-log :as t]
    [sixsq.nuvla.server.resources.spec.nuvlabox-log :as nl]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id             (str t/resource-type "/uuid")
                   :resource-type  t/resource-type
                   :parent         "nuvlabox/7b1ad037-a65e-41e0-8fdf-e0e8db30bb0b"
                   :created        timestamp
                   :updated        timestamp
                   :acl            valid-acl

                   :since          "1964-08-25T10:00:00.00Z"
                   :last-timestamp "2019-08-25T10:00:00.00Z"
                   :lines          10
                   :log            ["some\nlong\nlog\ninformation\n"]}]

    (stu/is-valid ::nl/schema root)
    (stu/is-invalid ::nl/schema (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :parent :created :updated :acl}]
      (stu/is-invalid ::nl/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:name :since :last-timestamp :lines :log}]
      (stu/is-valid ::nl/schema (dissoc root k)))))
