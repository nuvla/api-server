(ns sixsq.nuvla.server.resources.spec.module-application-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.module-application :as t]
    [sixsq.nuvla.server.resources.spec.module-application :as module-application]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id                      (str t/resource-type "/module-application-uuid")
                   :resource-type           t/resource-type
                   :created                 timestamp
                   :updated                 timestamp
                   :acl                     valid-acl

                   :author                  "someone"
                   :commit                  "wip"

                   :urls                    [["primary" "https://${host}:${port-443}/my/path"]
                                             ["other" "http://${host}:${port-80}/path"]]

                   :output-parameters       [{:name        "alpha"
                                              :description "my-alpha"}
                                             {:name        "beta"
                                              :description "my-beta"}
                                             {:name        "gamma"
                                              :description "my-gamma"}]

                   :environmental-variables [{:name  "ALPHA_ENV"
                                              :value "OK"}
                                             {:name        "BETA_ENV"
                                              :description "beta-env variable"
                                              :required    true}]

                   :files                   [{:file-name     "my-config.conf"
                                              :file-content "file content example"}
                                             {:file-name     "file_1"
                                              :file-content "file content example"}]

                   :docker-compose          "version: \"3.3\"\nservices:\n  web:\n    ..."}]

    (stu/is-valid ::module-application/schema root)
    (stu/is-invalid ::module-application/schema (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author :docker-compose}]
      (stu/is-invalid ::module-application/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit :urls :output-parameters :environmental-variables :files}]
      (stu/is-valid ::module-application/schema (dissoc root k)))))
















