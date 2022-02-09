(ns sixsq.nuvla.server.resources.spec.resource-log-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-log :as t]
    [sixsq.nuvla.server.resources.spec.resource-log :as rl]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1974-08-25T10:00:00.00Z"
        root      {:id             (str t/resource-type "/uuid")
                   :resource-type  t/resource-type
                   :parent         "nuvlabox/7b1ad037-a65e-41e0-8fdf-e0e8db30bb0b"
                   :created        timestamp
                   :updated        timestamp
                   :acl            valid-acl

                   :since          "1974-08-25T10:00:00.00Z"
                   :last-timestamp "2019-08-25T10:00:00.00Z"
                   :lines          10
                   :log            {:c1 ["log1" "log"] :c2 ["log2"]}
                   :components     ["c1" "c2"]}]

    (stu/is-valid ::rl/schema root)
    (stu/is-invalid ::rl/schema (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :parent :created :updated :acl :components}]
      (stu/is-invalid ::rl/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:name :since :last-timestamp :lines :log}]
      (stu/is-valid ::rl/schema (dissoc root k)))))
