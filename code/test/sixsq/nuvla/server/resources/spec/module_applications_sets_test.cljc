(ns sixsq.nuvla.server.resources.spec.module-applications-sets-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.module-application :as t]
    [sixsq.nuvla.server.resources.spec.module-applications-sets :as module-applications-sets]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id                (str t/resource-type "/module-application-uuid")
                   :resource-type     t/resource-type
                   :created           timestamp
                   :updated           timestamp
                   :acl               valid-acl

                   :author            "someone"
                   :commit            "wip"

                   :applications-sets [{:name         "x"
                                        :applications [{:id      "module/x"
                                                        :version 0}]}]}]

    (stu/is-valid ::module-applications-sets/schema root)
    (stu/is-valid ::module-applications-sets/schema (update root :applications-sets conj {:name         "y"
                                                                                          :applications [{:id      "module/y"
                                                                                                          :version 1}]}))
    (stu/is-valid ::module-applications-sets/schema (assoc-in root [:applications-sets 0 :applications 0 :environmental-variables]
                                                              [{:name  "var_1"
                                                                :value "var_1 value"}]))
    (stu/is-invalid ::module-applications-sets/schema (assoc root :badKey "badValue"))
    (stu/is-invalid ::module-applications-sets/schema (assoc root :applications-sets []))
    (stu/is-invalid ::module-applications-sets/schema (assoc-in root [:applications-sets 0 :applications] []))
    (stu/is-invalid ::module-applications-sets/schema (update-in root [:applications-sets 0] dissoc :name))
    (stu/is-invalid ::module-applications-sets/schema (update-in root [:applications-sets 0 :applications 0] dissoc :id))
    (stu/is-invalid ::module-applications-sets/schema (assoc-in root [:applications-sets 0 :applications 0 :id] "badid/x"))
    (stu/is-invalid ::module-applications-sets/schema (assoc-in root [:applications-sets 0 :applications 0 :version] -1))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author :applications-sets}]
      (stu/is-invalid ::module-applications-sets/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit}]
      (stu/is-valid ::module-applications-sets/schema (dissoc root k)))))
