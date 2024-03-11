(ns sixsq.nuvla.server.resources.spec.module-applications-sets-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.resources.spec.module-applications-sets :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id                (str module-application/resource-type "/module-application-uuid")
                   :resource-type     module-application/resource-type
                   :created           timestamp
                   :updated           timestamp
                   :acl               valid-acl

                   :author            "someone"
                   :commit            "wip"

                   :applications-sets [{:name         "x"
                                        :subtype      "docker"
                                        :applications [{:id      "module/x"
                                                        :version 0}]}]}]

    (stu/is-valid ::t/schema root)
    (stu/is-valid ::t/schema (update root :applications-sets conj {:name         "y"
                                                                   :applications [{:id      "module/y"
                                                                                   :version 1}]}))
    (stu/is-valid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :environmental-variables]
                                       [{:name  "var_1"
                                         :value "var_1 value"}]))
    (stu/is-valid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :registries-credentials]
                                       ["credential/ba2f41a3-c54c-fce8-32d2-0324e1c32ee2"]))
    (stu/is-invalid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :registries-credentials]
                                         []))
    (stu/is-valid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :files]
                                       [{:file-name    "my-config.conf"
                                         :file-content "file content example"}
                                        {:file-name    "file_1"
                                         :file-content "file content example"}]))
    (stu/is-invalid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :files]
                                         []))
    (stu/is-invalid ::t/schema (assoc root :badKey "badValue"))
    (stu/is-invalid ::t/schema (assoc root :applications-sets []))
    (stu/is-valid ::t/schema (assoc-in root [:applications-sets 0 :applications] []))
    (stu/is-invalid ::t/schema (assoc-in root [:applications-sets 0 :subtype] "wrong"))
    (stu/is-valid ::t/schema (update-in root [:applications-sets 0] dissoc :subtype))
    (stu/is-invalid ::t/schema (update-in root [:applications-sets 0] dissoc :name))
    (stu/is-invalid ::t/schema (update-in root [:applications-sets 0 :applications 0] dissoc :id))
    (stu/is-invalid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :id] "badid/x"))
    (stu/is-invalid ::t/schema (assoc-in root [:applications-sets 0 :applications 0 :version] -1))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author :applications-sets}]
      (stu/is-invalid ::t/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit}]
      (stu/is-valid ::t/schema (dissoc root k)))))
