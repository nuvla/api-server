(ns sixsq.nuvla.server.resources.spec.module-application-helm-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.module-application-helm :as t]
    [sixsq.nuvla.server.resources.spec.module-application-helm :as module-application-helm]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id                      (str t/resource-type "/module-application-helm-uuid")
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

                   :private-registries      ["infrastructure-service/uuid-1"
                                             "infrastructure-service/uuid-2"]

                   :files                   [{:file-name    "my-config.conf"
                                              :file-content "file content example"}
                                             {:file-name    "file_1"
                                              :file-content "file content example"}]

                   :helm-absolute-url       "http://x"
                   :helm-chart-name         "some-name"
                   :helm-chart-version      "some-version"
                   :helm-repo-cred          "credential/a"}]

    (stu/is-valid ::module-application-helm/schema root)
    (stu/is-invalid ::module-application-helm/schema (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author}]
      (stu/is-invalid ::module-application-helm/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit :urls :output-parameters :environmental-variables :files :private-registries :helm-absolute-url
                :helm-chart-name :helm-chart-version :helm-repo-cred}]
      (stu/is-valid ::module-application-helm/schema (dissoc root k)))))
